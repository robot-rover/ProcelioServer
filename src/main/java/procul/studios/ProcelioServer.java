package procul.studios;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.pojo.Server;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;
import java.util.Timer;

/**
 *
 */
//todo: add class description
public class ProcelioServer {
    private static Logger LOG = LoggerFactory.getLogger(ProcelioServer.class);
    private static File configFile = new File("./config.json");
    public static Random rn;
    public static Gson gson = new Gson();
    public static Server[] serverStatus;
    public ProcelioServer(){

    }
    public static void main(String[] args){
        rn = new Random();
        Configuration config = Configuration.loadConfiguration(configFile);
        if(args.length == 1 && args[0].equals("init")){
            String configText = Configuration.gson.toJson(config);
            try (FileWriter out = new FileWriter(configFile, false)) {
                out.write(configText);
            } catch (IOException e) {
                LOG.warn("Unable to initialize config file", e);
            }
            return;
        }
        config.partConfig = PartConfiguration.loadConfiguration(new File(config.partConfigPath));
        Database database = new Database(config);
        AtomicDatabase atomicDatabase = new AtomicDatabase(database.getContext());
        ClientEndpoints clientWrapper = new ClientEndpoints(database.getContext(), config, atomicDatabase);
        ServerEndpoints serverWrapper = new ServerEndpoints(database.getContext(), config, atomicDatabase);
        serverStatus = new Server[config.serverLocation.length];
        Timer serverDaemon = ServerDaemon.startDaemon(60000L, new ServerDaemon(config.serverLocation, serverStatus));
        SparkServer server = new SparkServer(config, clientWrapper, serverWrapper);
        server.start();
    }
}
