package procul.studios;

import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.Record2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.exception.RestException;
import procul.studios.pojo.response.Message;
import procul.studios.pojo.response.User;
import spark.Request;
import spark.Response;

import java.time.Instant;

import static procul.studios.exception.RestException.exception;
import static procul.studios.sqlbindings.Tables.*;
import static procul.studios.ProcelioServer.gson;

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
        try {
            authenticate(req, res);
        } catch (RestException e) {
            return e.getMessage();
        }
        String token = req.headers("X-User-Token");
        if(token == null)
            return exception(req, res, "Missing X-User-Token header", 400);
        Record2<Integer, Long> record = context.select(AUTHTABLE.USERID, AUTHTABLE.EXPIRES).from(AUTHTABLE).where(AUTHTABLE.TOKEN.eq(token)).fetchAny();
        if(record == null || Instant.ofEpochSecond(record.component2()).isBefore(Instant.now())){
            LOG.warn("{}: Attempted to validate a non-existant user with token: `{}`", req.attribute("requestID"), token);
            res.status(404);
            return gson.toJson(new Message("User could not be found"));
        }
        User user = new User();
        user.id = record.component1();
        res.header("X-User-Token", token);
        return gson.toJson(user);
    }

    public String addCurrency(Request req, Response res){
        try {
            authenticate(req, res);
        } catch (RestException e) {
            return e.getMessage();
        }
        final User[] toAdd = gson.fromJson(req.body(), User[].class);
        atomicDatabase.addOperation((context -> {
            for(User user : toAdd){
                if(user.id == null || user.currency == null){
                    LOG.warn("{}: Recieved user with id: `{}` and currency: `{}`", req.attribute("requestID"), user.id, user.currency);
                    continue;
                }
                Record1<Long> record = context.select(USERTABLE.CURRENCY).from(USERTABLE).where(USERTABLE.ID.eq(user.id)).fetchAny();
                if(record == null){
                    LOG.warn("{}: Nonexistant user recieved with id: `{}`", req.attribute("requestID"), user.id);
                    continue;
                }
                context.update(USERTABLE).set(USERTABLE.CURRENCY, record.component1() + user.currency).where(USERTABLE.ID.eq(user.id)).execute();
            }
        }));
        return gson.toJson(new Message("Currency added successfully"));
    }

    public void authenticate(Request req, Response res) throws RestException {
        String key = req.headers("Authorization");
        if(key == null)
            throw new RestException(req, res, "Missing authorization header", 401);
        if(!key.startsWith("Bearer "))
            throw new RestException(req, res, "Missing bearer statement from Authorization header", 401);
        key = key.substring("Bearer ".length());
        if(!key.equals(config.getServerKey()))
            throw new RestException(req, res, "Incorrect server key", 401);
        return;
    }
}
