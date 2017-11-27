import procul.studios.Configuration;
import procul.studios.ServerDaemon;
import procul.studios.pojo.Server;
import spark.Spark;

import java.io.File;

public class ServerDaemonTesting {
    public static void main(String[] args) throws InterruptedException {
        Spark.port(80);
        Spark.get("/*", (req, res) -> "{\n" +
                    "  \"name\": \"Test Server\",\n" +
                    "  \"hostname\": \"localhost\",\n" +
                    "  \"region\": 0,\n" +
                    "  \"usersOnline\": 3,\n" +
                    "  \"capacity\": 0,\n" +
                    "  \"isOnline\": true\n" +
                    "}"
        );
        Spark.init();

        Configuration config;
        File configFile = new File("config.json");
        config = Configuration.loadConfiguration(configFile);
        Server[] serverStatus = new Server[config.serverLocation.length];
        ServerDaemon.startDaemon(10000L, new ServerDaemon(config.serverLocation, serverStatus, true));
        Thread.sleep(60000L);
        System.exit(0);
    }
}
