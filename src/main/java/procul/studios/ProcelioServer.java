package procul.studios;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.pojo.Server;
import sun.rmi.runtime.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Timer;

/**
 *
 */
//todo: add class description
public class ProcelioServer {
    private static Logger LOG = LoggerFactory.getLogger(ProcelioServer.class);
    public static String url = "http://api.sovietbot.xyz";
    public static File configFile = new File("./config.json");
    public static Configuration config;
    public static Server[] serverStatus;
    public static Timer serverDaemon;
    public static int port;
    public static boolean useSsl = false;
    public static Gson gson = new Gson();
    public ProcelioServer(){

    }
    public static void main(String[] args){
        config = Configuration.loadConfiguration(configFile);
        if(args.length == 1 && args[0].equals("init")){
            String configText = Configuration.gson.toJson(config);
            try (FileWriter out = new FileWriter(configFile, false)) {
                out.write(configText);
            } catch (IOException e) {
                LOG.warn("Unable to initialize config file", e);
            }
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> Configuration.saveConfiguration(configFile, config)));
        Database database = new Database(config);
        DatabaseWrapper wrapper = new DatabaseWrapper(database.getContext());
        serverStatus = new Server[config.serverLocation.length];
        serverDaemon = ServerDaemon.startDaemon(60000L, new ServerDaemon(config.serverLocation, serverStatus));
        SparkServer server = new SparkServer(config, wrapper);
        server.start();
    }
}
