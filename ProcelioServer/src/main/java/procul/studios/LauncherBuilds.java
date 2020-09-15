package procul.studios;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.util.Hashing;
import procul.studios.util.OperatingSystem;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

public class LauncherBuilds {
    private static Logger LOG = LoggerFactory.getLogger(LauncherBuilds.class);

    class LauncherBuild {
        byte[] hash;
        File file;
        long length;

        public LauncherBuild(byte[] hash, File f, long length) {
            this.hash = hash;
            this.file = f;
            this.length = length;
        }
    }

    private Map<OperatingSystem, LauncherBuild> builds;
    public Map<OperatingSystem, LauncherBuild> getLauncherBuildMap() {
        return builds;
    }

    public LauncherBuild getBuild(OperatingSystem os) {
        return builds.get(os);
    }

    private static LauncherBuilds lb = null;
    public static LauncherBuilds getInstance() {return lb;}
    public static void createLauncherBuilds(Path baseDir) throws IOException {
        lb = new LauncherBuilds(baseDir);
    }

    private byte[] hash(File f) throws IOException {
        InputStream input = new BufferedInputStream(new FileInputStream(f));
        MessageDigest digest = Hashing.getMessageDigest();

        byte[] buffer = new byte[1024];
        int len;
        while((len = input.read(buffer)) > 0){
            digest.update(buffer, 0, len);
        }
        return digest.digest();
    }

    public LauncherBuilds(Path baseDir) throws IOException {
        builds = new HashMap<>();

        for (Path osDir : Files.newDirectoryStream(baseDir)) {
            if(!Files.isDirectory(osDir))
                continue;
            OperatingSystem os = OperatingSystem.parse(osDir.getFileName().toString());
            LOG.info("Launcher build " + os.getHeaderValue() + ": "+osDir);
            File[] fs = osDir.toFile().listFiles();
            if (fs == null || fs.length == 0)
                LOG.error("Build for "+osDir.toFile()+" not found");
            else if (fs[0].exists())
                builds.put(os, new LauncherBuild(hash(fs[0]), fs[0], fs[0].length()));
        }
    }
}
