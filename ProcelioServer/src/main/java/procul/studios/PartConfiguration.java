package procul.studios;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.gson.Configuration;
import procul.studios.pojo.Garages;
import procul.studios.pojo.Inventory;
import procul.studios.pojo.Robot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class PartConfiguration extends Configuration {
    private static Logger LOG = LoggerFactory.getLogger(PartConfiguration.class);

    //quantity is a value, cost is null
    String defaultInventory;

    transient Inventory loadedInventory;

    List<String> startingRobots;

    transient Garages loadedRobots;

    long startingCurrency;

    public static PartConfiguration loadConfiguration(String path) throws IOException {
        PartConfiguration config = loadGenericConfiguration(path, PartConfiguration.class);
        try {
            config.loadedInventory = new Inventory(Files.readAllBytes(Paths.get(config.defaultInventory)));
            List<Robot> robots = new ArrayList<>();
            for(String robotPath : config.startingRobots)
                robots.add(new Robot(Files.readAllBytes(Paths.get(robotPath))));
            config.loadedRobots = new Garages(robots);
        } catch (IOException e) {
            throw new IOException("Unable to read configuration file at " + path, e);
        }

        return config;
    }
}
