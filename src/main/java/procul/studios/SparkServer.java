package procul.studios;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.pojo.response.Message;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.stream.Collectors;

import static spark.Spark.*;

/**
 *
 */
//todo: add class description
public class SparkServer {
    static final Logger LOG = LoggerFactory.getLogger(SparkServer.class);
    int port;
    boolean isIgnited;
    DatabaseWrapper wrapper;
    public SparkServer(int port, DatabaseWrapper wrapper){
        this.port = port;
        isIgnited = false;
        this.wrapper = wrapper;
    }

    public void start(){
        secure("sovietbot.xyz.jks", ProcelioServer.keystorePass, null, null);
        port(port);
        before((req, res) -> res.type("application/json"));
        before((req, res) -> LOG.info("Request at {}\n{}\n{} ", req.pathInfo(), req.headers().stream().collect(Collectors.joining("\n")), req.body()));
        get("/status", this::status);
        post("/users", wrapper::createUser);
        post("/login", wrapper::login);
        get("/users/:user", wrapper::getUser);
        get("/avatars/:user", wrapper::getAvatar);
        post("/avatars", wrapper::setAvatar);
        notFound((req, res) -> wrapper.exception(req, res, "Route not found", 404));
        exception(Exception.class, (Exception e, Request req, Response res) -> LOG.error("Exception in Spark Thread", e));
        init();
        isIgnited = true;
    }

    public void stop(){
        Spark.stop();
        isIgnited = false;
    }

    public String status(Request req, Response res) {
        return wrapper.gson.toJson(new Message("Status 100: OK"));
    }
}
