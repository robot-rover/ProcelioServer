package procul.studios.pojo;

import com.google.gson.annotations.Expose;
import procul.studios.util.Tuple;
import procul.studios.util.Version;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PackageManifest {
    public List<String> delete;
    public Integer[] toVersion;
    public Integer[] fromVersion;
    public List<String> ignore;
    public String newExec;

    @Expose(serialize = false, deserialize = false)
    private transient File baseDir;
    public PackageManifest(File baseDir, Tuple<Version, Version> bridge){
        delete = new ArrayList<>();
        ignore = new ArrayList<>();
        this.baseDir = baseDir;
        this.fromVersion = bridge.getFirst().toArray();
        this.toVersion = bridge.getSecond().toArray();
    }

    public File getBaseDir(){
        return baseDir;
    }
}
