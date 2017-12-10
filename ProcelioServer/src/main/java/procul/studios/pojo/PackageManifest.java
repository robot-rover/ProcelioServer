package procul.studios.pojo;

import com.google.gson.annotations.Expose;
import procul.studios.util.Tuple;
import procul.studios.util.Version;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PackageManifest {
    public List<String> filesAndHashes;
    public List<String> ignore;
    public List<String> delete;
    public Integer[] toVersion;
    public Integer[] fromVersion;
    public String newExec;

    private transient File baseDir;
    public PackageManifest(File baseDir, Tuple<Version, Version> bridge){
        delete = new ArrayList<>();
        this.baseDir = baseDir;
        this.fromVersion = bridge.getFirst().toArray();
        this.toVersion = bridge.getSecond().toArray();
        this.ignore = new ArrayList<>();
        filesAndHashes = new ArrayList<>();
    }

    public File getBaseDir(){
        return baseDir;
    }
}
