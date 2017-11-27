package procul.studios;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.pojo.response.Message;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.stream.Collectors;

import static spark.Spark.*;
import static procul.studios.ProcelioServer.gson;

/**
 *
 */
//todo: add class description
public class SparkServer {
    static final Logger LOG = LoggerFactory.getLogger(SparkServer.class);
    boolean isIgnited;
    DatabaseWrapper wrapper;
    Configuration config;
    public SparkServer(Configuration config, DatabaseWrapper wrapper){
        isIgnited = false;
        this.wrapper = wrapper;
        this.config = config;
    }

    public void start(){
        //secure("sovietbot.xyz.jks", ProcelioServer.keystorePass, null, null);
        if(config.keystorePassB64 != null && config.keystorePath != null){
            secure(config.keystorePath, config.getKeystorePass(), null, null);
        }
        port(config.port);
        before((req, res) -> res.type("application/json"));
        before((req, res) -> LOG.info("Request at {}\n{}\n{} ", req.pathInfo(), req.headers().stream().map(v -> v + ": " + req.headers(v)).collect(Collectors.joining("\n")), req.body()));
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
        return gson.toJson(new Message("Status 100: OK"));
    }
}
