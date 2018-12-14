package procul.studios.util;

import java.io.File;

public class PackO {
    public Tuple<Version, Version> bridge;
    public byte[] hash;
    public File zip;
    public long length;

    public PackO(Tuple<Version, Version> bridge, byte[] hash, File zip, long length) {
        this.bridge = bridge;
        this.hash = hash;
        this.zip = zip;
        this.length = length;
    }
}
