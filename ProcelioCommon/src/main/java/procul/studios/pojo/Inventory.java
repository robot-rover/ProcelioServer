package procul.studios.pojo;

import procul.studios.util.BytesUtil;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Inventory {
    public static final int SERIALIZER_VERSION = 1;
    public static final int MAGIC_NUMBER = 0x10E10CBA;
    public final int versionRead;

    private Map<Short, Integer> parts;

    public Inventory(InputStream in) throws IOException {
        parts = new HashMap<>();
        int magicNumber = BytesUtil.readInt(in);
        if(magicNumber != MAGIC_NUMBER)
            throw new IOException("ByteStream does not contain a valid inventory");
        versionRead = BytesUtil.readInt(in);
        if(versionRead == 1) {
            int numberOfEntries = BytesUtil.readInt(in);
            for(int i = 0; i < numberOfEntries; i++) {
                short partId = BytesUtil.readShort(in);
                int quantity = BytesUtil.readInt(in);
                if(quantity != 0)
                    parts.put(partId, quantity);
            }
        } else {
            throw new UnsupportedEncodingException("Version " + versionRead + "not supported");
        }
    }

    public Iterable<Map.Entry<Short, Integer>> iterate() {
        return parts.entrySet();
    }

    public Inventory(Map<Short, Integer> entries) {
        this.parts = entries;
        versionRead = SERIALIZER_VERSION;
    }

    public void serialize(OutputStream out) throws IOException {
        BytesUtil.writeInt(out, MAGIC_NUMBER);
        BytesUtil.writeInt(out, SERIALIZER_VERSION);
        Set<Map.Entry<Short, Integer>> iterate = parts.entrySet();
        BytesUtil.writeInt(out, iterate.size());
        for(Map.Entry<Short, Integer> entry : iterate) {
            BytesUtil.writeShort(out, entry.getKey());
            BytesUtil.writeInt(out, entry.getValue());
        }
    }

    public Inventory(byte[] data) throws IOException {
        this(new ByteArrayInputStream(data));
    }

    public byte[] serialize() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        serialize(out);
        return out.toByteArray();
    }

    public int getStock(short partId) {
        return parts.getOrDefault(partId, 0);
    }

    public int changeStock(short partId, int delta) {
        int newValue = getStock(partId) + delta;
        return setStock(partId, newValue);
    }

    public int setStock(short partId, int newValue) {
        if(newValue == 0)
            parts.remove(partId);
        else
            parts.put(partId, newValue);
        return newValue;
    }

    public Inventory combine(PartTuple[] robot){
        for(PartTuple p : robot){
            changeStock(p.partId, 1);
        }
        return this;
    }

    public Inventory extract(PartTuple[] robot){
        for(PartTuple p : robot){
            changeStock(p.partId, -1);
        }
        return this;
    }

    public Map.Entry<Short, Integer> validate() {
        for(Map.Entry<Short, Integer> part: parts.entrySet()){
            if(part.getValue() < 0){
                return part;
            }
        }
        return null;
    }

    @Override
    public int hashCode() {
        return parts.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null)
            return false;
        if(obj == this)
            return true;
        if(obj instanceof Inventory)
            return ((Inventory) obj).parts.equals((this.parts));
        return false;
    }

    @Override
    public String toString() {
        return "Inventory v" + versionRead + " -> \n" +
                parts.toString();
    }
}
