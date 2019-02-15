package procul.studios.pojo.response;

import java.util.ArrayList;
import java.util.List;


public class LauncherConfiguration {
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
