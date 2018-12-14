package procul.studios.pojo.response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
}
