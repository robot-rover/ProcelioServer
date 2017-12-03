package procul.studios;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.pojo.Part;
import procul.studios.pojo.Robot;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static procul.studios.ProcelioServer.gson;

public class PartConfiguration {
    private static Logger LOG = LoggerFactory.getLogger(PartConfiguration.class);
    //cost is a value, quantity is null
    Part[] allParts;

    //quantity is a value, cost is null
    Map<String, Integer> defaultInventory;

    List<Robot> startingRobots;

    long startingCurrency;

    public static PartConfiguration loadConfiguration(File file) {
        PartConfiguration config;
        try (FileReader reader = new FileReader(file)){
            config = gson.fromJson(reader, PartConfiguration.class);
        } catch (FileNotFoundException e) {
            LOG.error("Configuration file does not exist at {}, using default...", file.getAbsolutePath());
            config = new PartConfiguration();
        } catch (IOException e) {
            LOG.error("Unable to read configuration file at " + file.getAbsolutePath() + ", using default...", e);
            config = new PartConfiguration();
        }
        return config;
    }

    public Part getPart(String partID){
        for(Part part : allParts){
            if(part.partID.equals(partID))
                return part;
        }
        return null;
    }
}
