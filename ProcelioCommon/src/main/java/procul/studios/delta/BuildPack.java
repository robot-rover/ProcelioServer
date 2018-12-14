package procul.studios.delta;

import procul.studios.util.Version;

import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Matcher;

public class BuildPack extends Pack implements Comparable<BuildPack> {
    Version buildVersion;
    public BuildPack(Path existingPack) throws IOException {
        super(existingPack);
        Matcher m = Build.pattern.matcher(archive.getFileName().toString());
        if(!m.find())
            throw new IOException("Could not Build version for " + archive);
        buildVersion = new Version(m.group(1), m.group(2), m.group(3));
    }

    public Version getVersion() {
        return buildVersion;
    }

    @Override
    public int compareTo(BuildPack o) {
        if(o == null)
            return 1;
        return buildVersion.compareTo(o.getVersion());
    }
}
