package procul.studios.delta;

import procul.studios.util.Tuple;
import procul.studios.util.Version;

import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Matcher;

public class DeltaPack extends Pack {
    public Tuple<Version, Version> getVersionBridge() {
        return versionBridge;
    }

    final private Tuple<Version, Version> versionBridge;
    public DeltaPack(Path existingPack) throws IOException {
        super(existingPack);
        Matcher m = Delta.pattern.matcher(archive.getFileName().toString());
        if(!m.find())
            throw new IOException("Could not parse Delta version for " + archive);
        versionBridge = new Tuple<>(
                new Version(m.group(1), m.group(2), m.group(3)),
                new Version(m.group(4), m.group(5), m.group(6))
        );
    }

    public Version getSource() {
        return versionBridge.getFirst();
    }
    public Version getTarget() {
        return versionBridge.getSecond();
    }
}
