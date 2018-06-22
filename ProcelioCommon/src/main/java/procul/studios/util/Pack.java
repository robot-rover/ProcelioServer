package procul.studios.util;

import java.io.File;

public class Pack {
    public Tuple<Version, Version> bridge;
    public byte[] hash;
    public File zip;
    public long length;

    public Pack(Tuple<Version, Version> bridge, byte[] hash, File zip, long length) {
        this.bridge = bridge;
        this.hash = hash;
        this.zip = zip;
        this.length = length;
    }

    public String getHashString(){
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }
}
