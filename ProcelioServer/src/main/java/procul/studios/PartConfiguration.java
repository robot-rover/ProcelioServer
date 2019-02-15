package procul.studios;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.pojo.Garages;
import procul.studios.pojo.Inventory;
import procul.studios.pojo.Part;
import procul.studios.pojo.Robot;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static procul.studios.gson.GsonSerialize.gson;

public class PartConfiguration {
    private static Logger LOG = LoggerFactory.getLogger(PartConfiguration.class);
    //cost is a value, quantity is null
    Part[] allParts;

    //quantity is a value, cost is null
    String defaultInventory;

    transient Inventory loadedInventory;

    List<String> startingRobots;

    transient Garages loadedRobots;

    long startingCurrency;

    public static PartConfiguration loadConfiguration(Path path) {
        PartConfiguration config;
        try (BufferedReader reader = Files.newBufferedReader(path)){
            config = gson.fromJson(reader, PartConfiguration.class);
            config.loadedInventory = new Inventory(Files.readAllBytes(Paths.get(config.defaultInventory)));
            List<Robot> robots = new ArrayList<>();
            for(String robotPath : config.startingRobots)
                robots.add(new Robot(Files.readAllBytes(Paths.get(robotPath))));
            config.loadedRobots = new Garages(robots);
        } catch (FileNotFoundException e) {
            LOG.error("Configuration file does not exist at {}, using default...", path);
            config = new PartConfiguration();
        } catch (IOException e) {
            LOG.error("Unable to read configuration file at " + path + ", using default...", e);
            config = new PartConfiguration();
        }

        return config;
    }

    public Part getPart(short partId){
        for(Part part : allParts){
            if(part.partId == partId)
                return part;
        }
        return null;
    }
}
