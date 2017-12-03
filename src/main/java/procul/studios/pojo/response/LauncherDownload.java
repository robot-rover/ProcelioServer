package procul.studios.pojo.response;

import java.util.List;

public class LauncherDownload {
    public String build;
    public List<String> patches;

    public LauncherDownload(String build){
        this.build = build;
    }
}
