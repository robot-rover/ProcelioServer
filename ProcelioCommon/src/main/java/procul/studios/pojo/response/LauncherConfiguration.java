package procul.studios.pojo.response;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class LauncherConfiguration {
    private static Logger LOG = LoggerFactory.getLogger(LauncherConfiguration.class);
    public String websiteUrl;
    public List<Update> updates;
    public Integer[] launcherVersion;
    public String quoteOfTheDay;
    public String quoteAuthor;

    public static class Update {
        public String title;
        public Integer[] version;
        public String description;
        public String hyperlink;
        public String image;
    }

    public LauncherConfiguration(){
        updates = new ArrayList<>();
    }

    public static LauncherConfiguration loadConfiguration(File file) {
        LauncherConfiguration config;
        try (FileReader reader = new FileReader(file)){
            config = new Gson().fromJson(reader, LauncherConfiguration.class);
        } catch (FileNotFoundException e) {
            LOG.error("Configuration file does not exist at {}, using default...", file.getAbsolutePath());
            config = new LauncherConfiguration();
        } catch (IOException e) {
            LOG.error("Unable to read configuration file at " + file.getAbsolutePath() + ", using default...", e);
            config = new LauncherConfiguration();
        }
        return config;
    }
}
