package procul.studios.pojo;

import procul.studios.util.BytesUtil;
import procul.studios.util.Hashing;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.util.Arrays;
import java.util.stream.Collectors;

public class Robot {
    public static final int SERIALIZER_VERSION = 1;
    public static final int MAGIC_NUMBER = 0xC571B040;
    public final int readVersion;
    public PartTuple[] partList;
    public String name;
    public byte[] metadata;
    public byte[] checksum;

    public Robot(String name, PartTuple[] partList) {
        this(name, new byte[8], partList);
    }

    @Override
    public String toString() {
        return "Robot v" + readVersion + " -> " +
                "\nName: " + name +
                "\nMetadata: " + Hashing.printHexBinary(metadata) +
                "\nChecksum: " + Hashing.printHexBinary(checksum) +
                "\nParts -> " + Arrays.stream(partList).map(PartTuple::toString).collect(Collectors.joining("\n\t", "\n\t", ""));

    }

    public Robot(String name, byte[] metadata, PartTuple[] partList){
        this.name = name;
        this.metadata = metadata;
        if(metadata.length != 8)
            throw new ArrayIndexOutOfBoundsException("Robot must have 8 bytes of metadata");
        this.partList = partList;
        readVersion = SERIALIZER_VERSION;
    }

    public Robot(InputStream in) throws IOException {
        int magicNumber = BytesUtil.readInt(in);
        if(magicNumber != MAGIC_NUMBER)
            throw new IOException("ByteStream does not contain a valid robot");
        readVersion = BytesUtil.readInt(in);
        if(readVersion == 1) {
            metadata = new byte[8];
            BytesUtil.readBytes(in, metadata);
            int nameLength = BytesUtil.readByte(in) & 0xFF;
            byte[] stringData = new byte[nameLength];
            BytesUtil.readBytes(in, stringData);
            name = new String(stringData, StandardCharsets.UTF_8);
            int numberOfParts = BytesUtil.readInt(in);
            partList = new PartTuple[numberOfParts];
            for(int i = 0; i < numberOfParts; i++)
                partList[i] = new PartTuple(in, readVersion);
            checksum = new byte[16];
            BytesUtil.readBytes(in, checksum);
        } else {
            throw new UnsupportedEncodingException("Version " + readVersion + "not supported");
        }
    }

    public Robot(byte[] data) throws IOException {
        this(new ByteArrayInputStream(data));
    }

    public byte[] serialize() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        serialize(out);
        return out.toByteArray();
    }

    public void serialize(OutputStream rawOut) throws IOException {
        DigestOutputStream out = new DigestOutputStream(rawOut, Hashing.getMessageDigest());
        BytesUtil.writeInt(out, MAGIC_NUMBER);
        BytesUtil.writeInt(out, SERIALIZER_VERSION);
        out.write(metadata);
        byte[] stringBytes = name.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(stringBytes.length, 255);
        out.write(length);
        out.write(stringBytes, 0, length);
        BytesUtil.writeInt(out, partList.length);
        for(PartTuple part : partList) {
            part.serialize(out);
        }
        rawOut.write(out.getMessageDigest().digest());
    }
}
