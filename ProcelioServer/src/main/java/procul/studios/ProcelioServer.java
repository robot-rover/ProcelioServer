package procul.studios;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.pojo.Server;
import procul.studios.pojo.response.LauncherConfiguration;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 *
 */
//todo: add class description
public class ProcelioServer {
    private static Logger LOG = LoggerFactory.getLogger(ProcelioServer.class);
    public static final Path configFile = Paths.get("./config.json").normalize();
    public static final Random rn = new Random();
    public static Server[] serverStatus;
    private static boolean multiThreaded = true;

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && args[0].equals("init")) {
            String configText = Configuration.gson.toJson(new Configuration());
            try (Writer out = Files.newBufferedWriter(configFile, StandardOpenOption.CREATE_NEW)) {
                out.write(configText);
            } catch (IOException e) {
                LOG.warn("Unable to initialize config file", e);
            }
            return;
        }
        Configuration config = Configuration.loadConfiguration(configFile, Configuration.class);
        if(args.length > 0 && args[0].equals("noThread")) {
            multiThreaded = false;
        }
        if (config.launcherConfigPath != null)
            config.launcherConfig = Configuration.loadConfiguration(Paths.get(config.launcherConfigPath), LauncherConfiguration.class);
        if (config.partConfigPath != null)
            config.partConfig = Configuration.loadConfiguration(Paths.get(config.partConfigPath), PartConfiguration.class);
        Path buildFolder = Paths.get(config.buildFolderPath);
        Collection<DiffManager> diffMangers = DiffManager.createDiffManagers(buildFolder).values();
        List<Thread> tList = diffMangers.stream().map(v -> new Thread(() -> {
            try {
                v.findPackages();
            } catch (IOException e) {
                e.printStackTrace();
            }
        })).collect(Collectors.toList());
        if(multiThreaded) {
            tList.forEach(Thread::start);
            tList.forEach(t -> {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    LOG.warn("Interrupted", e);
                }
            });
        } else {
            tList.forEach(Thread::run);
        }
        Database database = new Database(config);
        AtomicDatabase atomicDatabase = new AtomicDatabase(database.getContext());
        ClientEndpoints clientWrapper = new ClientEndpoints(database.getContext(), config, atomicDatabase);
        ServerEndpoints serverWrapper = new ServerEndpoints(database.getContext(), config, atomicDatabase);
        LauncherEndpoints launcherWrapper = new LauncherEndpoints(config);
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