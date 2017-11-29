package procul.studios;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.exception.RestException;
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
    ClientEndpoints client;
    ServerEndpoints server;
    Configuration config;
    public SparkServer(Configuration config, ClientEndpoints client, ServerEndpoints server){
        isIgnited = false;
        this.client = client;
        this.server = server;
        this.config = config;
    }

    public void start(){
        //secure("sovietbot.xyz.jks", ProcelioServer.keystorePass, null, null);
        if(config.keystorePassB64 != null && config.keystorePath != null){
            secure(config.keystorePath, config.getKeystorePass(), null, null);
        }
        port(config.port);
        before((req, res) -> res.type("application/json"));
        before((req, res) -> req.attribute("requestID", (req.headers("Authorization")!=null ? req.headers("Authorization").replace("Bearer ", "") : "NoAuth") + ";" + System.currentTimeMillis() + "IP" + req.ip() + req.pathInfo()));
        before((req, res) -> LOG.debug("RequestID `{}`\n{}\n{} ", req.attribute("requestID"), req.headers().stream().map(v -> v + ": " + req.headers(v)).collect(Collectors.joining("\n")), req.body()));
        //Client Endpoints
        get("/status", this::status);
        post("/users", client::createUser);
        post("/login", client::login);
        get("/users/:user", client::getUser);
        get("/avatars/:user", client::getAvatar);
        post("/avatars", client::setAvatar);

        //Server Endpoints
        get("/validate", server::validateToken);
        post("/reward", server::addCurrency);


        notFound((req, res) -> RestException.exception(req, res, "Route not found", 404));
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
