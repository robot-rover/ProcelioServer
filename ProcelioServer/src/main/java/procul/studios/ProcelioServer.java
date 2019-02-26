package procul.studios;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.pojo.Server;
import spark.utils.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Random;

/**
 *
 */
//todo: add class description
public class ProcelioServer {
    private static Logger LOG = LoggerFactory.getLogger(ProcelioServer.class);
    public static final Path configFile = Paths.get("./config.json").normalize();
    public static final Random rn = new Random();
    public static Server[] serverStatus;
    private static ServerConfiguration config;

    public static void main(String[] args) throws IOException {
        try {
            config = ServerConfiguration.loadConfiguration(configFile);
        } catch (IOException e) {
            throw new RuntimeException("Cannot Load Config", e);
        }
        Database database = new Database(config);
        AtomicDatabase atomicDatabase = new AtomicDatabase(database.getContext());
        ClientEndpoints clientWrapper = new ClientEndpoints(database.getContext(), config, atomicDatabase);
        ServerEndpoints serverWrapper = new ServerEndpoints(database.getContext(), config, atomicDatabase);
        LauncherEndpoints launcherWrapper = null;

        if(!StringUtils.isEmpty(config.buildFolderPath)) {
            Collection<DiffManager> diffMangers = DiffManager.createDiffManagers(Paths.get(config.buildFolderPath)).values();
            for (DiffManager diffManger : diffMangers) {
                diffManger.findPackages();
            }
            launcherWrapper = new LauncherEndpoints(config);
        }

        serverStatus = new Server[config.serverLocation.length];
        if (config.serverKeepAlive)
            ServerDaemon.startDaemon(60000L, new ServerDaemon(config.serverLocation, serverStatus));
        else for (int i = 0; i < config.serverLocation.length; i++) {
            serverStatus[i] = new Server(config.serverLocation[i]);
        }
        SparkServer server = new SparkServer(config, clientWrapper, serverWrapper, launcherWrapper);
        server.start();
    }
}