package procul.studios;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.pojo.response.Message;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.stream.Collectors;

import static procul.studios.util.GsonSerialize.gson;
import static spark.Spark.*;

/**
 *
 */
//todo: add class description
public class SparkServer {
    static final Logger LOG = LoggerFactory.getLogger(SparkServer.class);
    boolean isIgnited;
    ClientEndpoints client;
    ServerEndpoints server;
    LauncherEndpoints launcher;
    Configuration config;
    public SparkServer(Configuration config, ClientEndpoints client, ServerEndpoints server, LauncherEndpoints launcher){
        isIgnited = false;
        this.client = client;
        this.server = server;
        this.launcher = launcher;
        this.config = config;
    }

    public static String ex(String message, int code){
        halt(code, gson.toJson(new Message(message, 404)));
        return null;
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
        get("/servers", client::getServer);
        get("/parts", client::getInventory);
        post("/users", client::createUser);
        post("/login", client::login);
        get("/users/:user", client::getUser);
        patch("/users/me", client::editUser);
        get("/users/:user/avatar", client::getAvatar);
        post("/avatars", client::setAvatar);
        post("/purchase", client::blockTransaction);
        post("/users/:user/robots", client::createRobot);
        delete("/users/:user/robots/:robot", client::deleteRobot);
        patch("/users/:user/robots/:robot", client::editRobot);

        //Server Endpoints
        get("/validate", server::validateToken);
        post("/reward", server::addCurrency);

        //Launcher Endpoints
        get("/launcher/config", launcher::getConfig);
        //get("/launcher/logo", launcher::getLogo);
        get("/launcher", launcher::getPatchList);
        get("/launcher/build", launcher::fullBuild);
        get("/launcher/:patch", launcher::getPatchList);
        get("/launcher/patch/:patch", launcher::getPatch);
        get("/launcher/buildFile/:build/*", launcher::getBuildFile);


        notFound((req, res) -> gson.toJson(new Message("Route not found", 404)));
        exception(Exception.class, (Exception e, Request req, Response res) -> LOG.error("Exception in Spark Thread", e));
        init();
        isIgnited = true;
    }

    public void stop(){
        Spark.stop();
        isIgnited = false;
    }

    public String status(Request req, Response res) {
        return gson.toJson(new Message("Status 100: OK", 100));
    }
}
