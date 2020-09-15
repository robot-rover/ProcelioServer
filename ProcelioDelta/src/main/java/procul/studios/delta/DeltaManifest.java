package procul.studios.delta;

import procul.studios.util.GameVersion;
import procul.studios.util.Version;

import java.util.ArrayList;
import java.util.List;

public class DeltaManifest {
    public List<String> hashes;
    public List<String> delete;
    public GameVersion target;
    public GameVersion source;
    public String newExec;

    public DeltaManifest() {}

    public DeltaManifest(GameVersion source, GameVersion target) {
        delete = new ArrayList<>();
        this.source = source;
        this.target = target;
        hashes = new ArrayList<>();
    }
}
