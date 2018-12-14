package procul.studios;

import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.Record8;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.pojo.Part;
import procul.studios.pojo.PartTuple;
import procul.studios.pojo.Robot;
import procul.studios.pojo.request.Authenticate;
import procul.studios.pojo.response.Message;
import procul.studios.pojo.response.Token;
import procul.studios.pojo.response.User;
import procul.studios.util.GsonSerialize;
import procul.studios.util.GsonTuple;
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
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static procul.studios.ProcelioServer.rn;
import static procul.studios.SparkServer.ex;
import static procul.studios.sqlbindings.Tables.AUTHTABLE;
import static procul.studios.sqlbindings.Tables.USERTABLE;
import static procul.studios.util.GsonSerialize.gson;

public class ClientEndpoints {
    private static Logger LOG = LoggerFactory.getLogger(ClientEndpoints.class);
    DSLContext context;
    Configuration config;
    AtomicDatabase atomicDatabase;
    //String authFailed;
    final int imageDim = 128;

    public ClientEndpoints(DSLContext context, Configuration config, AtomicDatabase atomicDatabase) {
        this.context = context;
        this.config = config;
        this.atomicDatabase = atomicDatabase;
    }

    public String getInventory(Request req, Response res){
        authenticate(req, res);
        return gson.toJson(config.partConfig.allParts, Part[].class);
    }

    public String getServer(Request req, Response res){
        return gson.toJson(ProcelioServer.serverStatus);
    }

    public String blockTransaction(Request req, Response res){
        int id = authenticate(req, res);
        final Map<String, Integer> purchased = gson.fromJson(req.body(), User.inventoryType);
        if(purchased == null){
            return ex("No inventory in body", 400);
        }
        long costIter = 0;
        for(Map.Entry<String, Integer> part : purchased.entrySet()){
            Part partType = config.partConfig.getPart(part.getKey());
            if(partType == null){
                LOG.warn("{}: tried to buy nonexistant part {}", req.attribute("requestID"), part.getKey());
                continue;
            }
            if(part.getValue() == null){
                LOG.warn("{}: didn't initalize part.quantity to buy part {}. Buying 1", req.attribute("requestID"), part.getKey());
                part.setValue(1);
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
            String inventoryJson = context.select(USERTABLE.INVENTORY).from(USERTABLE).where(USERTABLE.ID.eq(id)).fetchAny().component1();
            Map<String, Integer> inventory = gson.fromJson(inventoryJson, User.inventoryType);
            for(Map.Entry<String, Integer> purchase : purchased.entrySet()){
                inventory.put(purchase.getKey(), inventory.getOrDefault(purchase.getKey(), 0) - purchase.getValue());
                if(inventory.get(purchase.getKey()) < 0){
                    res.status(400);
                    return new GsonTuple(new Message("Tried to sell " + -purchase.getValue() + " " + purchase.getKey() + " and you only have " + inventory.get(purchase.getKey()), 400), Message.class);
                }
            }
            context.update(USERTABLE).set(USERTABLE.INVENTORY, gson.toJson(inventory)).set(USERTABLE.CURRENCY, userCurrency).where(USERTABLE.ID.eq(id)).execute();
            User response = new User();
            response.currency = userCurrency;
            response.inventory = inventory;
            return new GsonTuple(response, User.class);
        });
        return proccessFuture(waitFor, gsonTuple -> gsonTuple.serialize(gson), req, res);
    }

    private <V> String proccessFuture(Future<V> future, Function<V, String> processor, Request req, Response res){
        V result = null;
        try {
            result = future.get(config.timeout, TimeUnit.SECONDS);
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
            return ex("Unable to read image bytes", 500);
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
            return ex("Server Error", 500);
        }
        context.update(USERTABLE).set(USERTABLE.AVATAR, out.toByteArray()).where(USERTABLE.ID.eq(id)).execute();
        User user = new User();
        user.avatar = "/users/" + String.valueOf(id) + "/avatar";
        user.id = id;
        return gson.toJson(user);
    }

    public String createRobot(Request req, Response res){
        int id = authenticate(req, res);
        if(!req.params(":user").equals(String.valueOf(id))){
            return ex("You can't change a bot that isn't yours", 403);
        }
        Robot toCreate = gson.fromJson(req.body(), Robot.class);
        if(toCreate == null){
            return ex("There is no Robot in the body", 400);
        }
        if(toCreate.name == null){
            return ex("The robot in the body does not have a name", 400);
        }

        final String name = toCreate.name;
        Future<GsonSerialize> created = atomicDatabase.addOperation(context -> {
            Record1<String> garageRecord = context.select(USERTABLE.ROBOTS).from(USERTABLE).where(USERTABLE.ID.eq(id)).fetchAny();
            if(garageRecord == null){
                LOG.warn("{}: SQL Error Record for user {} is null", req.params("requestID"), id);
                res.status(500);
                return new GsonTuple(new Message("Internal Server Error", 500), Message.class);
            }
            List<Robot> garage = gson.fromJson(garageRecord.component1(), User.garageType);
            garage.add(new Robot(name, new PartTuple[0]).setIDtoNext(garage));
            context.update(USERTABLE).set(USERTABLE.ROBOTS, gson.toJson(garage)).where(USERTABLE.ID.eq(id)).execute();
            return new GsonTuple(garage.get(garage.size() - 1), Robot.class);
        });
        return proccessFuture(created, json -> json.serialize(gson), req, res);
    }

    public String deleteRobot(Request req, Response res){
        int id = authenticate(req, res);
        if(!req.params(":user").equals(String.valueOf(id))){
            return ex("You can't change a bot that isn't yours", 403);
        }
        int robotToDelete;
        try {
            robotToDelete = Integer.parseInt(req.params(":robot"));
        } catch (NumberFormatException e){
            return ex(req.params(":robot") + " isn't an valid bot", 400);
        }
        Future<GsonSerialize> removed = atomicDatabase.addOperation(context -> {
            Record2<String, String> garageRecord = context.select(USERTABLE.ROBOTS, USERTABLE.INVENTORY).from(USERTABLE).where(USERTABLE.ID.eq(id)).fetchAny();
            if(garageRecord == null || garageRecord.component2() == null){
                LOG.warn("{}: No Robot or Inventory found for user");
                res.status(500);
                return new GsonTuple(new Message("Internal Server Error", 500), Message.class);
            }
            List<Robot> garage = gson.fromJson(garageRecord.component1(), User.garageType);
            Robot toRemove = garage.stream().filter(v -> v.id == robotToDelete).findAny().orElse(null);
            if(toRemove != null){
                garage.remove(toRemove);
                Map<String, Integer> inventory = Part.combine(gson.fromJson(garageRecord.component2(), User.inventoryType), toRemove.partList);
                context.update(USERTABLE).set(USERTABLE.ROBOTS, gson.toJson(garage)).set(USERTABLE.INVENTORY, gson.toJson(inventory)).where(USERTABLE.ID.eq(id)).execute();
                return new GsonTuple(garage, User.garageType);
            }
            res.status(404);
            return new GsonTuple(new Message("The robot could not be found", 404), Message.class);
        });
        return proccessFuture(removed, v -> v.serialize(gson), req, res);
    }

    public String editRobot(Request req, Response res){
        int id = authenticate(req, res);
        if(!req.params(":user").equals(String.valueOf(id))){
            return ex("You can't change a bot that isn't yours", 403);
        }
        int robotToEdit;
        try {
            robotToEdit = Integer.parseInt(req.params(":robot"));
        } catch (NumberFormatException e){
            return ex(req.params(":robot") + " isn't an valid bot", 400);
        }
        PartTuple[] newRobot = gson.fromJson(req.body(), PartTuple[].class);
        if(newRobot == null){
            return ex("No robot in your body", 400);
        }
        Future<GsonSerialize> result = atomicDatabase.addOperation(context -> {
            Record2<String, String> garageRecord = context.select(USERTABLE.ROBOTS, USERTABLE.INVENTORY).from(USERTABLE).where(USERTABLE.ID.eq(id)).fetchAny();
            List<Robot> robots = gson.fromJson(garageRecord.component1(), User.garageType);
            Robot robot = robots.stream().filter(v -> v.id == robotToEdit).findAny().orElse(null);
            if(robot == null){
                res.status(404);
                return new GsonTuple(new Message("Robot with id of " + robotToEdit + " could not be found", 404), Message.class);
            }
            Map<String, Integer> inventory = gson.fromJson(garageRecord.component2(), User.inventoryType);
            Map<String, Integer> availableParts = Part.combine(inventory, robot.partList);
            Map<String, Integer> newInventory = Part.extract(availableParts, newRobot);
            for(Map.Entry<String, Integer> part: newInventory.entrySet()){
                if(part.getValue() < 0){
                    res.status(400);
                    return new GsonTuple(new Message("You need " + part.getValue() + " more " + part.getKey() + " to make this change", 400), Message.class);
                }
            }
            robot.partList = newRobot;
            context.update(USERTABLE).set(USERTABLE.ROBOTS, gson.toJson(robots)).set(USERTABLE.INVENTORY, gson.toJson(newInventory)).where(USERTABLE.ID.eq(id)).execute();
            User response = new User();
            response.inventory = newInventory;
            response.garage = robots;
            return new GsonTuple(response, User.class);
        });
        return proccessFuture(result, json -> json.serialize(gson), req, res);
    }

    public String getUser(Request req, Response res) {
        Integer id = authenticate(req, res);
        Integer targetUser = null;
        String route = req.params(":user");
        boolean privledged = false;
        if(route.equals("me")){
            targetUser = id;
            privledged = true;
        }
        else {
            try {
                targetUser = Integer.parseInt(route);
            } catch (NumberFormatException e){
                return ex("TargetID Path not found", 404);
            }
        }
        Record8<Integer, String, Long, Integer, Integer, String, String, byte[]> record = context.select(USERTABLE.ID, USERTABLE.USERNAME, USERTABLE.CURRENCY, USERTABLE.USERTYPEFIELD, USERTABLE.XP, USERTABLE.INVENTORY, USERTABLE.ROBOTS, USERTABLE.AVATAR)
                .from(USERTABLE).where(USERTABLE.ID.eq(targetUser)).fetchAny();
        if(record == null){
            return ex("The user `" + String.valueOf(targetUser) + "` not found", 404);
        }
        User user = new User();
        user.id = record.component1();
        user.username = record.component2();
        user.userTypeField = record.component4();
        user.xp = record.component5();
        if(record.component8() != null && record.component8().length > 0){
            user.avatar = "/users/" + String.valueOf(targetUser) + "/avatar";
        } else {
            user.avatar = null;
        }
        if(privledged){
            user.currency = record.component3();
            user.inventory = gson.fromJson(record.component6(), User.inventoryType);
            user.garage = gson.fromJson(record.component7(), User.garageType);
        }
        return gson.toJson(user);
    }

    public Object getAvatar(Request req, Response res) {
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
            clearString(req.headers("X-Password"));
            return ex("Username or password is incorrect", 401);
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

    public String createUser(Request req, Response res) {
        Authenticate auth = gson.fromJson(req.body(), Authenticate.class);
        if(auth == null || auth.username == null || auth.password == null){
            return ex("Authentication Parameters are null", 400);
        }
        Record1<String> previous = context.select(USERTABLE.USERNAME).from(USERTABLE).where(USERTABLE.USERNAME.eq(auth.username)).fetchAny();
        if(previous != null){
            clearString(auth.password);
            return ex("The user `" + auth.username + "` already exists", 400);
        }
        String hashed = BCrypt.hashpw(auth.password, BCrypt.gensalt());
        clearString(auth.password);
        context.insertInto(USERTABLE).set(USERTABLE.CURRENCY, config.partConfig.startingCurrency).set(USERTABLE.USERNAME, auth.username).set(USERTABLE.PASSWORDHASH, hashed)
                .set(USERTABLE.XP, 0).set(USERTABLE.INVENTORY, gson.toJson(config.partConfig.defaultInventory)).set(USERTABLE.LASTLOGIN, Instant.now().getEpochSecond())
                .set(USERTABLE.USERTYPEFIELD, 0).set(USERTABLE.ROBOTS, gson.toJson(config.partConfig.startingRobots)).execute();
        clearString(auth.password);
        return gson.toJson(new Message("User created successfully"));
    }

    public String login(Request req, Response res) {
        Authenticate auth = gson.fromJson(new InputStreamReader(new ByteArrayInputStream(req.bodyAsBytes())), Authenticate.class);
        if(auth == null || auth.username == null || auth.password == null){
            return ex("Authentication Parameters are null", 400);
        }
        Record2<String, Integer> databaseHash = context.select(USERTABLE.PASSWORDHASH, USERTABLE.ID).from(USERTABLE).where(USERTABLE.USERNAME.eq(auth.username)).fetchAny();
        if (databaseHash == null || databaseHash.component1() == null) {
            clearString(auth.password);
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
            long expires = Instant.now().plus(1, ChronoUnit.DAYS).getEpochSecond();
            context.deleteFrom(AUTHTABLE).where(AUTHTABLE.USERID.eq(id)).execute();
            context.insertInto(AUTHTABLE).set(AUTHTABLE.USERID, databaseHash.component2()).set(AUTHTABLE.CLIENTIP, ip).set(AUTHTABLE.EXPIRES, expires).set(AUTHTABLE.TOKEN, token).execute();
            context.update(USERTABLE).set(USERTABLE.LASTLOGIN, Instant.now().getEpochSecond()).where(USERTABLE.ID.eq(id)).execute();
            clearString(auth.password);
            return gson.toJson(new Token(token, id, expires));
        } else {
            clearString(auth.password);
            return ex("Username or password is incorrect", 401);
        }
    }

    private void clearString(String clear) {
        try {
            Field field = String.class.getDeclaredField("value");
            field.setAccessible(true);
            char[] value = (char[]) field.get(clear);
            for (int i = 0; i < value.length; i++) {
                value[i] = 0;
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            LOG.warn("Unable to erase String value", e);
        }
    }
}
