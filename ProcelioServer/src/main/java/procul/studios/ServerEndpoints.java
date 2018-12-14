package procul.studios;

import org.jooq.DSLContext;
import org.jooq.Record2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.pojo.response.Message;
import procul.studios.pojo.response.User;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.time.Instant;

import static procul.studios.SparkServer.ex;
import static procul.studios.sqlbindings.Tables.AUTHTABLE;
import static procul.studios.sqlbindings.Tables.USERTABLE;
import static procul.studios.util.GsonSerialize.gson;

public class ServerEndpoints {
    private static Logger LOG = LoggerFactory.getLogger(ServerEndpoints.class);
    DSLContext context;
    Configuration config;
    AtomicDatabase atomicDatabase;

    public ServerEndpoints(DSLContext context, Configuration config, AtomicDatabase atomicDatabase){
        this.context = context;
        this.config = config;
        this.atomicDatabase = atomicDatabase;
    }

    public String validateToken(Request req, Response res){
        authenticate(req, res);
        String token = req.headers("X-User-Token");
        if(token == null){
            res.status(400);
            return ex("Missing X-User-Token header", 400);
        }
        Record2<Integer, Long> record = context.select(AUTHTABLE.USERID, AUTHTABLE.EXPIRES).from(AUTHTABLE).where(AUTHTABLE.TOKEN.eq(token)).fetchAny();
        if(record == null || Instant.ofEpochSecond(record.component2()).isBefore(Instant.now())){
            LOG.warn("{}: Attempted to validate a non-existant user with token: `{}`", req.attribute("requestID"), token);
            return ex("User could not be found", 404);
        }
        User user = new User();
        user.id = record.component1();
        res.header("X-User-Token", token);
        return gson.toJson(user);
    }

    public String addCurrency(Request req, Response res){
        authenticate(req, res);
        final User[] toAdd = gson.fromJson(req.body(), User[].class);
        atomicDatabase.addOperation((context -> {
            for(User user : toAdd){
                if(user.rewardCheck()){
                    LOG.warn("{}: Recieved user with id: `{}`, currency: `{}`, and xp: `{}`", req.attribute("requestID"), user.id, user.currency, user.xp);
                    continue;
                }
                Record2<Long, Integer> record = context.select(USERTABLE.CURRENCY, USERTABLE.XP).from(USERTABLE).where(USERTABLE.ID.eq(user.id)).fetchAny();
                if(record == null){
                    LOG.warn("{}: Nonexistant user recieved with id: `{}`", req.attribute("requestID"), user.id);
                    continue;
                }
                context.update(USERTABLE).set(USERTABLE.CURRENCY, record.component1() + user.currency).set(USERTABLE.XP, record.component2() + user.xp).where(USERTABLE.ID.eq(user.id)).execute();
            }
        }));
        return gson.toJson(new Message("Rewards added successfully"));
    }

    public void authenticate(Request req, Response res) {
        String key = req.headers("Authorization");
        if(key == null)
            Spark.halt(401, gson.toJson(new Message("Missing authorization header", 401)));
        if(!key.startsWith("Bearer "))
            Spark.halt(401, gson.toJson(new Message("Missing bearer statement from Authorization header", 401)));
        key = key.substring("Bearer ".length());
        if(!key.equals(config.getServerKey()))
            Spark.halt(401, gson.toJson(new Message("Incorrect server key", 401)));
    }
}
