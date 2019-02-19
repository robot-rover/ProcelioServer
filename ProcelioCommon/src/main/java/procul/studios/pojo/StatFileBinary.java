package procul.studios.pojo;

import procul.studios.util.BytesUtil;

import java.io.*;
import java.util.Arrays;
import java.util.stream.Collectors;

public class StatFileBinary {
    public static final int SERIALIZER_VERSION = 1;
    public static final int MAGIC_NUMBER = 0x1EF1A757;

    public final int versionRead;
    public Block[] blocks;

    public StatFileBinary(InputStream in) throws IOException {
        int magicNumber = BytesUtil.readInt(in);
        if(magicNumber != MAGIC_NUMBER)
            throw new IOException("ByteStream does not contain a valid statfile");
        versionRead = BytesUtil.readInt(in);
        if(versionRead == 1) {
            int blockArrayLength = BytesUtil.readInt(in);
            blocks = new Block[blockArrayLength];
            for(int i = 0; i < blockArrayLength; i++) {
                short partId = BytesUtil.readShort(in);
                int flagArrayLength = BytesUtil.readByte(in) & 0xFF;
                Flag[] flags = new Flag[flagArrayLength];
                for(int j = 0; j < flagArrayLength; j++) {
                    byte type = BytesUtil.readByte(in);
                    int value = BytesUtil.readInt(in);
                    flags[j] = new Flag(type, value);
                }
                blocks[i] = new Block(partId, flags);
            }
        } else {
            throw new UnsupportedEncodingException("Version " + versionRead + "not supported");
        }
    }

    public StatFileBinary(Block[] blockArray){
        this.blocks = blockArray;
        versionRead = SERIALIZER_VERSION;
    }

    @Override
    public String toString() {
        return "StatFile v" + versionRead + " -> \n" +
                Arrays.stream(blocks).map(Block::toString).collect(Collectors.joining("\n"));
    }

    public StatFileBinary(byte[] bytes) throws IOException {
        this(new ByteArrayInputStream(bytes));
    }

    public byte[] serialize() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        serialize(out);
        return out.toByteArray();
    }

    public void serialize(OutputStream out) throws IOException {
        BytesUtil.writeInt(out, MAGIC_NUMBER);
        BytesUtil.writeInt(out, SERIALIZER_VERSION);
        BytesUtil.writeInt(out, blocks.length);
        for(Block block : blocks) {
            BytesUtil.writeShort(out, block.partId);
            BytesUtil.writeByte(out, (byte) block.flags.length);
            for(Flag flag : block.flags) {
                    BytesUtil.writeByte(out, flag.type);
                BytesUtil.writeInt(out, flag.value);
            }
        }
    }

    public static class Block {
        public Block(short partId, Flag[] flags) {
            this.partId = partId;
            this.flags = flags;
        }
        public final short partId;
        public final Flag[] flags;

        @Override
        public String toString() {
            return toString("");
        }

        public String toString(String indent) {
            String newIndent = indent + "\t";
            return indent + "Part " + partId + "\n" + Arrays.stream(flags).map(v -> v.toString(newIndent)).collect(Collectors.joining("\n"));
        }
    }

    public static class Flag {
        public Flag(byte type, int value) {
            this.type = type;
            this.value = value;
        }
        public final byte type;
        public final int value;

        @Override
        public String toString() {
            return toString("");
        }

        public String toString(String indent) {
            return indent + (type & 0xFF) + " -> " + value;
        }
    }
}
