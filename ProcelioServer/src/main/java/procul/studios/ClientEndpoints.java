package procul.studios;

import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.Record6;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.gson.GsonSerialize;
import procul.studios.gson.GsonTuple;
import procul.studios.gson.StatFile;
import procul.studios.pojo.Robot;
import procul.studios.pojo.*;
import procul.studios.pojo.request.Authenticate;
import procul.studios.pojo.response.Message;
import procul.studios.pojo.response.Token;
import procul.studios.pojo.response.User;
import procul.studios.util.Hashing;
import spark.Request;
import spark.Response;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static procul.studios.ProcelioServer.rn;
import static procul.studios.SparkServer.ex;
import static procul.studios.gson.GsonSerialize.gson;
import static procul.studios.sqlbindings.Tables.AUTHTABLE;
import static procul.studios.sqlbindings.Tables.USERTABLE;

public class ClientEndpoints {
    private static Logger LOG = LoggerFactory.getLogger(ClientEndpoints.class);
    DSLContext context;
    AtomicDatabase atomicDatabase;
    PartConfiguration partConfig;
    StatFile statFile;
    byte[] statFileBytes;
    String statFileChecksum;
    int timeout;
    final int imageDim = 128;

    public ClientEndpoints(DSLContext context, ServerConfiguration config, AtomicDatabase atomicDatabase) {
        this.context = context;
        this.atomicDatabase = atomicDatabase;
        this.timeout = config.timeout;
        try {
            partConfig = PartConfiguration.loadConfiguration(config.partConfigPath);
        } catch (IOException e) {
            throw new RuntimeException("Client Module Unable to load part config", e);
        }
        try {
            statFile = StatFile.loadConfiguration(config.statFileSource);
            ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
            DigestOutputStream out = new DigestOutputStream(bytesOut, Hashing.getMessageDigest());
            statFile.export().serialize(out);
            statFileBytes = bytesOut.toByteArray();
            statFileChecksum = Hashing.printHexBinary(out.getMessageDigest().digest());
        } catch (IOException e) {
            throw new RuntimeException("Client Module unable tot load statfile", e);
        }
    }

    public String getStatFileChecksum(Request req, Response res) {
        return gson.toJson(new Message(statFileChecksum));
    }

    public Object getStatFile(Request req, Response res) {
        HttpServletResponse raw = res.raw();
        res.type("application/octet-stream");

        try {
            raw.getOutputStream().write(statFileBytes);
            raw.getOutputStream().flush();
            raw.getOutputStream().close();

        } catch (IOException e) {
            LOG.error("{}: Error writing statfile to stream", req.attribute("requestID"), e);
            return ex("Internal Serer Error", 500);
        }

        return res.raw();
    }

    private int getRequestedUser(Request req, int ownId) {
        String userString = req.params(":user");
        if(userString.equals("me"))
            return ownId;
        else {
            int id;
            try {
                id = Integer.parseInt(userString);
            } catch (NumberFormatException e) {
                ex(userString + " is not a valid user", 400);
                return 0;
            }
            return id;
        }
    }

    public String blockTransaction(Request req, Response res) throws IOException {
        int id = authenticate(req, res);
        final Inventory purchased;
        try {
            purchased = new Inventory(req.bodyAsBytes());
        } catch (IOException e) {
            return ex("Inventory Binary is Invalid", 403);
        }
        long costIter = 0;
        for(Map.Entry<Short, Integer> part : purchased.iterate()){
            StatFile.Block partType = statFile.getPart(part.getKey());
            if(partType == null){
                LOG.warn("{}: tried to buy nonexistant part {}", req.attribute("requestID"), part.getKey());
                continue;
            }
            if(part.getValue() == null){
                LOG.warn("{}: didn't initalize part.quantity to buy part {}. Buying none", req.attribute("requestID"), part.getKey());
            }
            costIter += partType.cost * part.getValue();
        }
        final long cost = costIter;
        Future<GsonSerialize> waitFor = atomicDatabase.addOperation((DSLContext context) -> {
            Record1<Long> currRecord = context.select(USERTABLE.CURRENCY).from(USERTABLE).where(USERTABLE.ID.eq(id)).fetchAny();
            if(currRecord == null || currRecord.component1() == null){
                LOG.warn("{}: BlockTransaction proceeded to SQL stage but unable to find user with id {}", req.attribute("requestID"), id);
                res.status(500);
                return new GsonTuple(new Message("Something went wrong with our database", 500), Message.class);
            }
            long userCurrency = currRecord.component1();
            if(userCurrency < cost){
                res.status(400);
                return new GsonTuple(new Message("You need " + (cost - userCurrency) + " more credits to do this!", 400), Message.class);
            }
            userCurrency -= cost;
            Inventory inventory = new Inventory(context.select(USERTABLE.INVENTORY).from(USERTABLE).where(USERTABLE.ID.eq(id)).fetchAny().component1());
            for(Map.Entry<Short, Integer> purchase : purchased.iterate()){
                int newQuantity = inventory.changeStock(purchase.getKey(), purchase.getValue());
                if(newQuantity < 0){
                    res.status(400);
                    return new GsonTuple(new Message("Tried to sell " + -purchase.getValue() + " of the part " + purchase.getKey() + " and you only have " + (newQuantity - purchase.getValue()), 400), Message.class);
                }
            }
            context.update(USERTABLE).set(USERTABLE.INVENTORY, inventory.serialize()).set(USERTABLE.CURRENCY, userCurrency).where(USERTABLE.ID.eq(id)).execute();
            return new GsonTuple(new Message("The transaction completed successfully."), Message.class);
        });
        return processFuture(waitFor, gsonTuple -> gsonTuple.serialize(gson), req, res);
    }

    private <V> String processFuture(Future<V> future, Function<V, String> processor, Request req, Response res){
        V result = null;
        try {
            result = future.get(timeout, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("{}: Error waiting for atomic server", req.attribute("requestID"), e);
            ex("Internal Server Error", 500);
        } catch (TimeoutException e) {
            LOG.error("{}: Timeout waiting for atomic server", req.attribute("requestID"), e);
            ex("The server is under heavy load right now", 500);
        }
        return processor.apply(result);
    }

    @SuppressWarnings("ConstantConditions")
    public int authenticate(Request req, Response res) {
        String token = req.headers("Authorization");
        if(token == null)
            ex("Missing authorization header", 401);
        if(!token.startsWith("Bearer "))
            ex("Missing bearer statement from Authorization header", 401);
        token = token.substring("Bearer ".length());
        Record2<Integer, Long> record = context.select(AUTHTABLE.USERID, AUTHTABLE.EXPIRES).from(AUTHTABLE).where(AUTHTABLE.TOKEN.eq(token)).fetchAny();
        if(record == null)
            ex("Token is missing or malformed", 401);
        if(record.component2() < Instant.now().getEpochSecond())
            ex("Your access token has expired", 401);
        res.header("X-Token-Expires-At", String.valueOf(record.component2()));
        return record.component1();
    }

    public String setAvatar(Request req, Response res) {
        int id = authenticate(req, res);
        BufferedImage avatar;
        try {
            avatar = ImageIO.read(new ByteArrayInputStream(req.bodyAsBytes()));
        } catch (IOException e) {
            LOG.error("{}: Error reading image bytes", req.attribute("requestID"), e);
            return ex("Unable to read image bytes", 400);
        }
        BufferedImage resized = new BufferedImage(imageDim, imageDim, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int sx1, sx2, sy1, sy2, offset;
        if(avatar.getWidth() > avatar.getHeight()){
            sy1 = 0;
            sy2 = avatar.getHeight();
            offset = (avatar.getWidth()-avatar.getHeight())/2;
            sx1 = offset;
            sx2 = offset + avatar.getHeight();
        } else {
            sx1 = 0;
            sx2 = avatar.getWidth();
            offset = (avatar.getHeight() - avatar.getWidth())/2;
            sy1 = offset;
            sy2 = offset + avatar.getWidth();
        }
        g.drawImage(avatar, 0, 0, imageDim, imageDim, sx1, sy1, sx2, sy2, null);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(resized, "png", out);
        } catch (IOException e) {
            LOG.error("{}: Unable to write image to stream", req.attribute("requestID"), e);
            return ex("Server Error", 400);
        }
        context.update(USERTABLE).set(USERTABLE.AVATAR, out.toByteArray()).where(USERTABLE.ID.eq(id)).execute();
        User user = new User();
        user.avatar = "/users/" + String.valueOf(id) + "/avatar";
        user.id = id;
        return gson.toJson(user);
    }

    public String createRobot(Request req, Response res) throws IOException {
        int id = authenticate(req, res);
        Robot toCreate = gson.fromJson(req.body(), Robot.class);
        if(toCreate == null){
            return ex("There is no Robot in the body", 400);
        }
        if(toCreate.name == null){
            return ex("The robot in the body does not have a name", 400);
        }

        final String name = toCreate.name;
        Future<GsonSerialize> created = atomicDatabase.addOperation(context -> {
            Record1<byte[]> garageRecord = context.select(USERTABLE.ROBOTS).from(USERTABLE).where(USERTABLE.ID.eq(id)).fetchAny();
            if(garageRecord == null){
                LOG.warn("{}: SQL Error Record for user {} is null", req.params("requestID"), id);
                res.status(500);
                return new GsonTuple(new Message("Internal Server Error", 500), Message.class);
            }
            Garages garage = new Garages(garageRecord.component1());
            int newRobotId = garage.addRobot(new Robot(name, new PartTuple[0]));
            context.update(USERTABLE).set(USERTABLE.ROBOTS, garage.serialize()).where(USERTABLE.ID.eq(id)).execute();
            return new GsonTuple(new RobotInfo(newRobotId, name), RobotInfo.class);
        });
        return processFuture(created, json -> json.serialize(gson), req, res);
    }

    public String deleteRobot(Request req, Response res){
        int id = authenticate(req, res);
        int robotToDelete;
        try {
            robotToDelete = Integer.parseInt(req.params(":robot"));
        } catch (NumberFormatException e){
            return ex(req.params(":robot") + " isn't an valid bot", 400);
        }
        Future<GsonSerialize> removed = atomicDatabase.addOperation(context -> {
            Record2<byte[], byte[]> garageRecord = context.select(USERTABLE.ROBOTS, USERTABLE.INVENTORY).from(USERTABLE).where(USERTABLE.ID.eq(id)).fetchAny();
            if(garageRecord == null || garageRecord.component2() == null){
                LOG.warn("{}: No Robot or Inventory found for user");
                res.status(500);
                return new GsonTuple(new Message("Internal Server Error", 500), Message.class);
            }
            Garages garage = new Garages(garageRecord.component1());
            Robot toRemove = garage.removeRobot(robotToDelete);
            if(toRemove != null){
                Inventory inventory = new Inventory(garageRecord.component2()).combine(toRemove.partList);
                context.update(USERTABLE).set(USERTABLE.ROBOTS, garage.serialize()).set(USERTABLE.INVENTORY, inventory.serialize()).where(USERTABLE.ID.eq(id)).execute();
                return new GsonTuple(new Message("The robot was deleted successfully."), Message.class);
            }
            res.status(404);
            return new GsonTuple(new Message("The robot could not be found", 404), Message.class);
        });
        return processFuture(removed, v -> v.serialize(gson), req, res);
    }

    public String editRobot(Request req, Response res) throws IOException {
        int id = authenticate(req, res);
        int robotToEdit;
        try {
            robotToEdit = Integer.parseInt(req.params(":robot"));
        } catch (NumberFormatException e){
            return ex(req.params(":robot") + " isn't an valid bot", 400);
        }
        Robot newRobot = new Robot(req.bodyAsBytes());
        Future<GsonSerialize> result = atomicDatabase.addOperation(context -> {
            Record2<byte[], byte[]> garageRecord = context.select(USERTABLE.ROBOTS, USERTABLE.INVENTORY).from(USERTABLE).where(USERTABLE.ID.eq(id)).fetchAny();
            Garages robots = new Garages(garageRecord.component1());
            Robot oldRobot = robots.getRobot(robotToEdit);
            if(oldRobot == null){
                res.status(404);
                return new GsonTuple(new Message("Robot with id of " + robotToEdit + " could not be found", 404), Message.class);
            }
            Inventory inventory = new Inventory(garageRecord.component2()).combine(oldRobot.partList).extract(newRobot.partList);
            Map.Entry<Short, Integer> missing = inventory.validate();
            if(missing != null) {
                res.status(400);
                return new GsonTuple(new Message("You need " + missing.getValue() + " more of part " + missing.getKey() + " to make this change", 400), Message.class);
            }
            robots.updateRobot(robotToEdit, newRobot);
            context.update(USERTABLE).set(USERTABLE.ROBOTS, robots.serialize()).set(USERTABLE.INVENTORY, inventory.serialize()).where(USERTABLE.ID.eq(id)).execute();
            return new GsonTuple(new Message("The robot was editted successfully."), Message.class);
        });
        return processFuture(result, json -> json.serialize(gson), req, res);
    }

    public String getUser(Request req, Response res) {
        int id = authenticate(req, res);
        int targetUser = getRequestedUser(req, id);
        Record6<Integer, String, Long, Integer, Integer, byte[]> record = context.select(USERTABLE.ID, USERTABLE.USERNAME, USERTABLE.CURRENCY, USERTABLE.USERTYPEFIELD, USERTABLE.XP, USERTABLE.AVATAR)
                .from(USERTABLE).where(USERTABLE.ID.eq(targetUser)).fetchAny();
        if(record == null){
            return ex("The user `" + targetUser + "` not found", 404);
        }
        User user = new User();
        user.id = record.component1();
        user.username = record.component2();
        user.currency = record.component3();
        user.userTypeField = record.component4();
        user.xp = record.component5();
        if(record.component6() != null && record.component6().length > 0){
            user.avatar = "/users/" + targetUser + "/avatar";
        } else {
            user.avatar = null;
        }
        return gson.toJson(user);
    }

    public Object getAvatar(Request req, Response res) {
        int id = authenticate(req, res);
        String route = req.params(":user");
        int targetUser;
            try {
                targetUser = Integer.parseInt(route);
            } catch (NumberFormatException e){
                return ex("TargetID Path not found", 404);
            }

        Record2<byte[], Integer> record = context.select(USERTABLE.AVATAR, USERTABLE.ID).from(USERTABLE).where(USERTABLE.ID.eq(targetUser)).fetchAny();
        if(record == null){
            res.status(404);
            return gson.toJson(new Message("The user `" + String.valueOf(targetUser) + "` not found", 404));
        }
        if(record.component1() == null || record.component1().length == 0){
            return ex("This user doesn't have an avatar", 404);
        }
        HttpServletResponse raw = res.raw();
        res.type("image/png");
        try {
            raw.getOutputStream().write(record.component1());
            raw.getOutputStream().flush();
            raw.getOutputStream().close();

        } catch (IOException e) {
            LOG.error("{}: Error writing avatar image to stream", req.attribute("requestID"), e);
            return ex("Internal Serer Error", 500);
        }
        return res.raw();
    }

    public String editUser(Request req, Response res) {
        int id = authenticate(req, res);
        Authenticate newCredentials = gson.fromJson(req.body(), Authenticate.class);
        Record1<String> databaseHash = context.select(USERTABLE.PASSWORDHASH).from(USERTABLE)
                .where(USERTABLE.ID.eq(id).and(USERTABLE.USERNAME.eq(req.headers("X-Username")))).fetchAny();
        if(req.headers("X-Password") == null || req.headers("X-Username") == null){
            return ex("Credentials incorrect", 401);
        }
        if (databaseHash == null || databaseHash.component1() == null) {
            return ex("Username or password is incorrect", 401);
        }
        if (newCredentials.username != null) {
            Record1<Integer> collision = context.select(USERTABLE.ID).from(USERTABLE).where(USERTABLE.USERNAME.eq(newCredentials.username)).fetchAny();
            if(collision != null) {
                return ex("Username is already taken", 409);
            }
        }
        if (BCrypt.checkpw(req.headers("X-Password"), databaseHash.component1())) {
            context.update(USERTABLE)
                    .set(USERTABLE.USERNAME, newCredentials.username == null ? req.headers("X-Username") : newCredentials.username)
                    .set(USERTABLE.PASSWORDHASH, newCredentials.password == null ? req.headers("X-Password") : BCrypt.hashpw(newCredentials.password, BCrypt.gensalt()))
                    .where(USERTABLE.ID.eq(id))
                    .execute();
            return gson.toJson(new Message("User credentials changed successfully"));
        }
        return ex("Credentials incorrect", 401);
    }

    public String createUser(Request req, Response res) throws IOException {
        Authenticate auth = gson.fromJson(req.body(), Authenticate.class);
        if(auth == null || auth.username == null || auth.password == null){
            return ex("Authentication Parameters are null", 400);
        }
        Record1<String> previous = context.select(USERTABLE.USERNAME).from(USERTABLE).where(USERTABLE.USERNAME.eq(auth.username)).fetchAny();
        if(previous != null){
            return ex("The user `" + auth.username + "` already exists", 409);
        }
        String hashed = BCrypt.hashpw(auth.password, BCrypt.gensalt());
        context.insertInto(USERTABLE).set(USERTABLE.CURRENCY, partConfig.startingCurrency).set(USERTABLE.USERNAME, auth.username).set(USERTABLE.PASSWORDHASH, hashed)
                .set(USERTABLE.XP, 0).set(USERTABLE.INVENTORY, partConfig.loadedInventory.serialize()).set(USERTABLE.LASTLOGIN, Instant.now().getEpochSecond())
                .set(USERTABLE.USERTYPEFIELD, 0).set(USERTABLE.ROBOTS, partConfig.loadedRobots.serialize()).execute();
        return gson.toJson(new Message("User created successfully"));
    }

    public String login(Request req, Response res) {
        Authenticate auth = gson.fromJson(new InputStreamReader(new ByteArrayInputStream(req.bodyAsBytes())), Authenticate.class);
        if(auth == null || auth.username == null || auth.password == null){
            return ex("Authentication Parameters are null", 400);
        }
        if (auth.noexpire == null)
            auth.noexpire = false;
        Record2<String, Integer> databaseHash = context.select(USERTABLE.PASSWORDHASH, USERTABLE.ID).from(USERTABLE).where(USERTABLE.USERNAME.eq(auth.username)).fetchAny();
        if (databaseHash == null || databaseHash.component1() == null) {
            return ex("Username or password is incorrect", 401);
        }
        if (BCrypt.checkpw(auth.password, databaseHash.component1())) {
            byte[] bytes = new byte[12];
            rn.nextBytes(bytes);
            String token = new String(Base64.getEncoder().encode(bytes), StandardCharsets.UTF_8);
            int id = databaseHash.component2();
            String ip = req.headers("CF-Connecting-IP");
            if(ip == null)
                ip = req.ip();
            long expires = (auth.noexpire) ? Instant.now().plus(5000, ChronoUnit.DAYS).getEpochSecond() : Instant.now().plus(1, ChronoUnit.DAYS).getEpochSecond();
            context.deleteFrom(AUTHTABLE).where(AUTHTABLE.USERID.eq(id)).execute();
            context.insertInto(AUTHTABLE).set(AUTHTABLE.USERID, databaseHash.component2()).set(AUTHTABLE.CLIENTIP, ip).set(AUTHTABLE.EXPIRES, expires).set(AUTHTABLE.TOKEN, token).execute();
            context.update(USERTABLE).set(USERTABLE.LASTLOGIN, Instant.now().getEpochSecond()).where(USERTABLE.ID.eq(id)).execute();
            return gson.toJson(new Token(token, id, expires));
        } else {
            return ex("Username or password is incorrect", 401);
        }
    }

    public String getRobots(Request req, Response res) throws IOException {
        int id = authenticate(req, res);
        Record1<byte[]> record = context.select(USERTABLE.ROBOTS).from(USERTABLE).where(USERTABLE.ID.eq(id)).fetchAny();
        if(record.component1() == null)
            return ex("The requested user " + id + " doesn't exist", 404);
        Garages garages = new Garages(record.component1());
        RobotInfo[] info = new RobotInfo[garages.getSize()];
        int i = 0;
        for(Map.Entry<Integer, Robot> entry : garages.iterate()) {
            info[i++] = new RobotInfo(entry.getKey(), entry.getValue().name);
        }
        return gson.toJson(info);
    }

    public Object getRobot(Request req, Response res) throws IOException {
        int id = authenticate(req, res);
        int target = getRequestedUser(req, id);
        int robotToEdit;
        try {
            robotToEdit = Integer.parseInt(req.params(":robot"));
        } catch (NumberFormatException e){
            return ex(req.params(":robot") + " isn't an valid bot", 400);
        }
        Record1<byte[]> record = context.select(USERTABLE.ROBOTS).from(USERTABLE).where(USERTABLE.ID.eq(target)).fetchAny();
        if(record.component1() == null)
            return ex("The requested user " + target + " doesn't exist", 404);
        Garages garages = new Garages(record.component1());
        Robot toGet = garages.getRobot(robotToEdit);

        HttpServletResponse raw = res.raw();
        res.type("application/octet-stream");

        try {
            raw.getOutputStream().write(toGet.serialize());
            raw.getOutputStream().flush();
            raw.getOutputStream().close();

        } catch (IOException e) {
            LOG.error("{}: Error writing robot to stream", req.attribute("requestID"), e);
            return ex("Internal Serer Error", 500);
        }

        return res.raw();
    }

    public Object getInventory(Request req, Response res) throws IOException {
        int id = authenticate(req, res);
        Record1<byte[]> record = context.select(USERTABLE.INVENTORY).from(USERTABLE).where(USERTABLE.ID.eq(id)).fetchAny();
        Inventory inventory = new Inventory(record.component1());

        HttpServletResponse raw = res.raw();
        res.type("application/octet-stream");

        try {
            raw.getOutputStream().write(inventory.serialize());
            raw.getOutputStream().flush();
            raw.getOutputStream().close();

        } catch (IOException e) {
            LOG.error("{}: Error writing robot to stream", req.attribute("requestID"), e);
            return ex("Internal Serer Error", 500);
        }

        return res.raw();
    }
}
