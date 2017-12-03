package procul.studios;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.pojo.Server;
import procul.studios.pojo.response.LauncherDownload;

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
    public static final File configFile = new File("./config.json");
    public static final Random rn = new Random();
    public static final Gson gson = new Gson();
    public static Server[] serverStatus;

    public static void main(String[] args){
        Configuration config = Configuration.loadConfiguration(configFile);
        if (args.length > 0) {
            DiffManager differ = new DiffManager(config);
            if(args[0].equals("diff"))
                differ.createPatches();
            else if(args[0].equals("rediff")) {
                differ.clearPatches();
                differ.createPatches();
            } else if(args[0].equals("package")) {
                differ.generatePackages();
            } else if(args[0].equals("repackage")) {
                differ.clearPatches();
                differ.clearPackages();
                differ.generatePackages();
            } else if(args[0].equals("init")){
                String configText = Configuration.gson.toJson(config);
                try (FileWriter out = new FileWriter(configFile, false)) {
                    out.write(configText);
                } catch (IOException e) {
                    LOG.warn("Unable to initialize config file", e);
                }
            }
            return;
        }
        if(config.partConfig == null)
            config.partConfig = PartConfiguration.loadConfiguration(new File(config.partConfigPath));
        DiffManager differ = new DiffManager(config);
        differ.generatePackages();
        Database database = new Database(config);
        AtomicDatabase atomicDatabase = new AtomicDatabase(database.getContext());
        ClientEndpoints clientWrapper = new ClientEndpoints(database.getContext(), config, atomicDatabase);
        ServerEndpoints serverWrapper = new ServerEndpoints(database.getContext(), config, atomicDatabase);
        LauncherEndpoints launcherWrapper = new LauncherEndpoints(config, differ);
        serverStatus = new Server[config.serverLocation.length];
        Timer serverDaemon = ServerDaemon.startDaemon(60000L, new ServerDaemon(config.serverLocation, serverStatus));
        SparkServer server = new SparkServer(config, clientWrapper, serverWrapper, launcherWrapper);
        server.start();
    }
}
