package procul.studios.delta;

import procul.studios.util.Version;

import java.util.ArrayList;
import java.util.List;

public class DeltaManifest {
    public List<String> hashes;
    public List<String> delete;
    public Integer[] target;
    public Integer[] source;
    public String newExec;

    public DeltaManifest() {}

    public DeltaManifest(Version source, Version target) {
        delete = new ArrayList<>();
        this.source = source.toArray();
        this.target = target.toArray();
        hashes = new ArrayList<>();
    }
}
