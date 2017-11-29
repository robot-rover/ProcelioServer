package procul.studios;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.jooq.*;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.exception.RestException;
import procul.studios.pojo.Part;
import procul.studios.pojo.Robot;
import procul.studios.pojo.request.Authenticate;

import static procul.studios.sqlbindings.Tables.*;

import procul.studios.pojo.response.Inventory;
import procul.studios.pojo.response.Message;
import procul.studios.pojo.response.Token;
import procul.studios.pojo.response.User;
import spark.Request;
import spark.Response;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static procul.studios.exception.RestException.exception;
import static procul.studios.ProcelioServer.gson;
import static procul.studios.ProcelioServer.rn;

public class ClientEndpoints {
    private static Logger LOG = LoggerFactory.getLogger(ClientEndpoints.class);
    DSLContext context;
    Configuration config;
    AtomicDatabase atomicDatabase;
    //String authFailed;
    final int imageDim = 128;

    public ClientEndpoints(DSLContext context, Configuration config, AtomicDatabase atomicDatabase) {
        this.context = context;
        gson = new GsonBuilder().create();
        this.config = config;
        this.atomicDatabase = atomicDatabase;
    }

    public String getServer(Request req, Response res){
        int id;
        try {
            id = authenticate(req, res);
        } catch (RestException e) {
            return e.getMessage();
        }
        return gson.toJson(ProcelioServer.serverStatus);
    }

    public String blockTransaction(Request req, Response res){
        int id;
        try {
            id = authenticate(req, res);
        } catch (RestException e) {
            return e.getMessage();
        }
        final Part[] purchased = gson.fromJson(req.body(), Part[].class);
        long costIter = 0;
        for(Part part : purchased){
            Part partType = config.partConfig.getPart(part.partID);
            if(partType == null){
                LOG.warn("{}: tried to buy nonexistant part {}", req.attribute("requestID"), part.partID);
                continue;
            }
            if(part.quantity == null){
                LOG.warn("{}: didn't initalize part.quantity to buy part {}. Buying 1", req.attribute("requestID"), part.partID);
                part.quantity = 1;
            }
            costIter += partType.cost * part.quantity;
        }
        final long cost = costIter;
        Future<Util.GsonTuple> waitFor = atomicDatabase.addOperation((DSLContext context) -> {
            Record1<Long> currRecord = context.select(USERTABLE.CURRENCY).from(USERTABLE).where(USERTABLE.ID.eq(id)).fetchAny();
            if(currRecord == null){
                LOG.warn("{}: BlockTransaction proceeded to SQL stage but unable to find user with id {}", req.attribute("requestID"), id);
                return new Util.GsonTuple(new Message("Something went wrong with our database", 500), Message.class);
            }
            long userCurrency = currRecord.component1();
            if(userCurrency < cost)
                return new Util.GsonTuple(new Message("You need " + (cost - userCurrency) + " more credits to do this!", 400), Message.class);
            userCurrency -= cost;
            String inventoryJson = context.select(USERTABLE.INVENTORY).from(USERTABLE).where(USERTABLE.ID.eq(id)).fetchAny().component1();
            List<Part> inventory = gson.fromJson(inventoryJson, new TypeToken<List<Part>>(){}.getType());
            for(Part purchase : purchased){
                Part reference = Util.findEqualOrInsert(inventory, purchase, () -> new Part(purchase.partID, 0));
                if(reference.quantity + purchase.quantity < 0)
                    return new Util.GsonTuple(new Message("Tried to sell " + -purchase.quantity + " " + purchase.partID + " and you only have " + reference.quantity, 400), Message.class);
                reference.quantity += purchase.quantity;
            }
            context.update(USERTABLE).set(USERTABLE.INVENTORY, gson.toJson(inventory)).set(USERTABLE.CURRENCY, userCurrency).where(USERTABLE.ID.eq(id)).execute();
            Inventory response = new Inventory();
            response.currency = userCurrency;
            response.parts = inventory.toArray(new Part[inventory.size()]);
            return new Util.GsonTuple(response, Inventory.class);
        });
        Util.GsonTuple result;
        try {
            result = waitFor.get(config.timeout, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("{}: Error waiting for atomic server", req.attribute("requestID"), e);
            return exception(req, res, "Internal Server Error", 500);
        } catch (TimeoutException e) {
            LOG.error("{}: Timeout waiting for atomic server", req.attribute("requestID"), e);
            return exception(req, res, "The server is under heavy load right now", 500);
        }
        return result.serialize(gson);
    }

    public int authenticate(Request req, Response res) throws RestException {
        String token = req.headers("Authorization");
        if(token == null)
            throw new RestException(req, res, "Missing authorization header", 401);
        if(!token.startsWith("Bearer "))
            throw new RestException(req, res, "Missing bearer statement from Authorization header", 401);
        token = token.substring("Bearer ".length());
        Record2<Integer, Long> record = context.select(AUTHTABLE.USERID, AUTHTABLE.EXPIRES).from(AUTHTABLE).where(AUTHTABLE.TOKEN.eq(token)).fetchAny();
        if(record == null)
            throw new RestException(req, res, "Token is missing or malformed", 401);
        if(record.component2() < Instant.now().getEpochSecond())
            throw new RestException(req, res, "Your access token has expired", 401);
        res.header("X-Token-Expires-At", String.valueOf(record.component2()));
        return record.component1();
    }

    public String setAvatar(Request req, Response res) {
        int id;
        try {
            id = authenticate(req, res);
        } catch (RestException e) {
            return e.getMessage();
        }
        BufferedImage avatar;
        try {
            avatar = ImageIO.read(new ByteArrayInputStream(req.bodyAsBytes()));
        } catch (IOException e) {
            return exception(req, res, "Unable to read image bytes", 400);
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
            return exception(req, res, "Server Error", 500);
        }
        context.update(USERTABLE).set(USERTABLE.AVATAR, out.toByteArray()).where(USERTABLE.ID.eq(id)).execute();
        User user = new User();
        user.avatar = config.url + "/avatars/" + String.valueOf(id);
        user.id = id;
        return gson.toJson(user);
    }

    public String getUser(Request req, Response res) {
        Integer userid = null;
        try {
            userid = authenticate(req, res);
        } catch (RestException e) {
            return e.getMessage();
        }
        Integer targetUser = null;
        String route = req.params(":user");
        boolean privledged = false;
        if(route.equals("me")){
            targetUser = userid;
            privledged = true;
        }
        else {
            try {
                targetUser = Integer.parseInt(route);
            } catch (NumberFormatException e){
                return exception(req, res, "TargetID Path not found", 404);
            }
        }
        Record8<Integer, String, Long, Integer, Integer, String, String, byte[]> record = context.select(USERTABLE.ID, USERTABLE.USERNAME, USERTABLE.CURRENCY, USERTABLE.USERTYPEFIELD, USERTABLE.XP, USERTABLE.INVENTORY, USERTABLE.ROBOTS, USERTABLE.AVATAR)
                .from(USERTABLE).where(USERTABLE.ID.eq(targetUser)).fetchAny();
        if(record == null)
            return exception(req, res, "The user `" + String.valueOf(targetUser) + "` not found", 404);
        User user = new User();
        user.id = record.component1();
        user.username = record.component2();
        user.userTypeField = record.component4();
        user.xp = record.component5();
        if(record.component8() != null && record.component8().length > 0){
            user.avatar = config.url + "/avatars/" + String.valueOf(targetUser);
        } else {
            user.avatar = "";
        }
        if(privledged){
            user.currency = record.component3();
            user.inventory = gson.fromJson(record.component6(), Part[].class);
            user.garage = gson.fromJson(record.component7(), Robot[].class);
        }
        return gson.toJson(user);
    }

    public Object getAvatar(Request req, Response res) {
        String route = req.params(":user");
        int targetUser;
            try {
                targetUser = Integer.parseInt(route);
            } catch (NumberFormatException e){
                return exception(req, res, "TargetID Path not found", 404);
            }

        Record2<byte[], Integer> record = context.select(USERTABLE.AVATAR, USERTABLE.ID).from(USERTABLE).where(USERTABLE.ID.eq(targetUser)).fetchAny();
        if(record == null){
            return exception(req, res, "The user `" + String.valueOf(targetUser) + "` not found", 404);
        }
        if(record.component1() == null || record.component1().length == 0){
            return exception(req, res, "This user doesn't have an avatar", 404);
        }
        HttpServletResponse raw = res.raw();
        res.type("image/png");
        try {
            raw.getOutputStream().write(record.component1());
            raw.getOutputStream().flush();
            raw.getOutputStream().close();

        } catch (IOException e) {
            LOG.error("{}: Error writing avatar image to stream", req.attribute("requestID"), e);
            return exception(req, res, "Internal Server Error", 500);
        }
        return res.raw();
    }

    public String createUser(Request req, Response res) {
        Authenticate auth = gson.fromJson(req.body(), Authenticate.class);
        if(auth == null || auth.username == null || auth.password == null){
            return exception(req, res, "Authentication Parameters are null", 400);
        }
        Record1<String> previous = context.select(USERTABLE.USERNAME).from(USERTABLE).where(USERTABLE.USERNAME.eq(auth.username)).fetchAny();
        if(previous != null){
            clearString(auth.password);
            return exception(req, res, "The user `" + auth.username + "` already exists", 400);
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
            return exception(req, res, "Authentication Parameters are null", 400);
        }
        Record2<String, Integer> databaseHash = context.select(USERTABLE.PASSWORDHASH, USERTABLE.ID).from(USERTABLE).where(USERTABLE.USERNAME.eq(auth.username)).fetchAny();
        if (databaseHash == null || databaseHash.component1() == null) {
            clearString(auth.password);
            return exception(req, res, "Username or password is incorrect", 400);
        }
        if (BCrypt.checkpw(auth.password, databaseHash.component1())) {
            byte[] bytes = new byte[12];
            rn.nextBytes(bytes);
            String token = new String(Base64.getEncoder().encode(bytes), StandardCharsets.UTF_8);
            int id = databaseHash.component2();
            String ip = req.ip();
            long expires = Instant.now().plus(1, ChronoUnit.DAYS).getEpochSecond();
            context.deleteFrom(AUTHTABLE).where(AUTHTABLE.USERID.eq(id)).execute();
            context.insertInto(AUTHTABLE).set(AUTHTABLE.USERID, databaseHash.component2()).set(AUTHTABLE.CLIENTIP, ip).set(AUTHTABLE.EXPIRES, expires).set(AUTHTABLE.TOKEN, token).execute();
            context.update(USERTABLE).set(USERTABLE.LASTLOGIN, Instant.now().getEpochSecond()).where(USERTABLE.ID.eq(id)).execute();
            clearString(auth.password);
            return gson.toJson(new Token(token, id, expires));
        } else {
            clearString(auth.password);
            return exception(req, res, "Username or password is incorrect", 400);
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
