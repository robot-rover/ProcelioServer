package procul.studios;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jooq.*;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.exception.RestException;
import procul.studios.pojo.Part;
import procul.studios.pojo.Robot;
import procul.studios.pojo.request.Authenticate;

import static procul.studios.ProcelioServer.url;
import static procul.studios.sqlbindings.Tables.*;

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
import java.util.Random;

public class DatabaseWrapper {
    private static Logger LOG = LoggerFactory.getLogger(DatabaseWrapper.class);
    DSLContext context;
    final Gson gson;
    //String authFailed;
    Random rn;
    final long startingCurrency = 200;
    final int imageDim = 128;
    final Part[] defaultInventory = {new Part("testBlock", 100)};
    final Robot[] defaultRobots = {
            new Robot(new PartTuple[]{
                    new PartTuple("testBlock", new int[]{0, 0, 0, 0, 0, 0}),
                    new PartTuple("testWheel", new int[]{0, 1, 0, 0, 0, 0})
            })
    };

    public DatabaseWrapper(DSLContext context) {
        this.context = context;
        gson = new GsonBuilder().create();
        rn = new Random();
    }

    public int authenticate(Request req, Response res) throws RestException {
        String token = req.headers("Authorization");
        if(token == null)
            throw new RestException(exception(req, res, "Missing authroriztion header", 401));
        if(!token.startsWith("Bearer "))
            throw new RestException(exception(req, res, "Missing bearer statement from Authorization header", 401));
        token = token.substring("Bearer ".length());
        Record2<Integer, Long> record = context.select(AUTHTABLE.USERID, AUTHTABLE.EXPIRES).from(AUTHTABLE).where(AUTHTABLE.TOKEN.eq(token)).fetchAny();
        if(record == null)
            throw new RestException(exception(req, res, "Token is missing or malformed", 401));
        if(record.component2() < Instant.now().getEpochSecond())
            throw new RestException(exception(req, res, "Your access token has expired", 401));
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
            LOG.error("Unable to write image to stream", e);
            return exception(req, res, "Server Error", 500);
        }
        context.update(USERTABLE).set(USERTABLE.AVATAR, out.toByteArray()).where(USERTABLE.ID.eq(id)).execute();
        User user = new User();
        user.avatar = url + "/avatars/" + String.valueOf(id);
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
            user.avatar = url + "/avatars/" + String.valueOf(targetUser);
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
            LOG.error("Error writing avatar image to stream", e);
            return exception(req, res, "Internal Server Error", 500);
        }
        return res.raw();
    }

    public String exception(Request req, Response res, String message, int code) {
        res.status(code);
        return gson.toJson(new Message("Code " + String.valueOf(code) + ": " + message));

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
        context.insertInto(USERTABLE).set(USERTABLE.CURRENCY, startingCurrency).set(USERTABLE.USERNAME, auth.username).set(USERTABLE.PASSWORDHASH, hashed)
                .set(USERTABLE.XP, 0).set(USERTABLE.INVENTORY, gson.toJson(defaultInventory)).set(USERTABLE.LASTLOGIN, Instant.now().getEpochSecond())
                .set(USERTABLE.USERTYPEFIELD, 0).set(USERTABLE.ROBOTS, gson.toJson(defaultRobots)).execute();
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
