package procul.studios.pojo.response;

import java.util.List;

public class LauncherDownload {
    public String build;
    public List<String> patches;
    public boolean upToDate;
    public Integer[] launcherVersion;

    public LauncherDownload(String build, boolean upToDate, Integer[] launcherVersion){
        this.build = build;
        this.upToDate = upToDate;
        this.launcherVersion = launcherVersion;
    }
}
