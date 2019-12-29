package procul.studios.gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class LauncherConfiguration extends Configuration {
    public String websiteUrl;
    public String[] launcherArguments;
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

    public static LauncherConfiguration loadConfiguration(String path) throws IOException {
        return loadGenericConfiguration(path, LauncherConfiguration.class);
    }
}
