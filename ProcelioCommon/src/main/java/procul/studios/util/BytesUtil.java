package procul.studios.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class BytesUtil {
    private BytesUtil() { }

    public static void writeByte(OutputStream out, byte b) throws IOException {
        out.write(b & 0xFF);
    }

    public static void readBytes(InputStream in, byte[] data) throws IOException {
        int length = in.read(data);
        if(length != data.length)
            throw new IOException("Reached end of Stream");
    }

    public static byte readByte(InputStream in) throws IOException {
        int datem = in.read();
        if(datem < 0)
            throw new IOException("Reached end of Stream");
        return (byte) datem;
    }

    // Little Endian
    public static void writeInt(OutputStream out, int i) throws IOException {
        out.write(i & 0xFF);
        out.write((i >> 8) & 0xFF);
        out.write((i >> 16) & 0xFF);
        out.write((i >> 24) & 0xFF);
    }

    public static int readInt(InputStream in) throws IOException {
        int result = 0;
        for(int off = 0; off <= 24; off += 8) {
            int datem = in.read();
            if(datem < 0)
                throw new IOException("Reached end of Stream");
            result |= datem << off;
        }
        return result;
    }

    public static int readInt(byte[] bytes) {
        int result = bytes[0] & 0xFF;
        result |= ((bytes[1] & 0xFF) << 8);
        result |= ((bytes[2] & 0xFF) << 16);
        result |= ((bytes[3] & 0xFF) << 24);
        return result;
    }

    public static void writeLong(OutputStream out, long l) throws IOException {
        out.write((int) (l & 0xFF));
        out.write((int) ((l >> 8) & 0xFF));
        out.write((int) ((l >> 16) & 0xFF));
        out.write((int) ((l >> 24) & 0xFF));
        out.write((int) ((l >> 32) & 0xFF));
        out.write((int) ((l >> 40) & 0xFF));
        out.write((int) ((l >> 48) & 0xFF));
        out.write((int) ((l >> 56) & 0xFF));
    }

    public static long readLong(InputStream in) throws IOException {
        long result = 0;
        for(int off = 0; off <= 56; off += 8) {
            int datem = in.read();
            if(datem < 0)
                throw new IOException("Reached end of Stream");
            result |= ((long) datem) << off;
        }
        return result;
    }

    public static void writeFloat(OutputStream out, float f) throws IOException {
        writeInt(out, Float.floatToRawIntBits(f));
    }

    public static float readFloat(InputStream in) throws IOException {
        return Float.intBitsToFloat(readInt(in));
    }

    public static void writeDouble(OutputStream out, double d) throws IOException {
        writeLong(out, Double.doubleToRawLongBits(d));
    }

    public static double readDouble(InputStream in) throws IOException {
        return Double.longBitsToDouble(readLong(in));
    }

    public static void writeShort(OutputStream out, short s) throws IOException {
        out.write(s & 0xFF);
        out.write((s >> 8) & 0xFF);
    }

    public static short readShort(InputStream in) throws IOException {
        short result = 0;
        for(int off = 0; off <= 8; off += 8) {
            int datem = in.read();
            if(datem < 0)
                throw new IOException("Reached end of Stream");
            result |= datem << off;
        }
        return result;
    }

    public static void writeBoolean(OutputStream out, boolean b) throws IOException {
        out.write(b ? 0x01 : 0x00);
    }

    public static boolean readBoolean(InputStream in) throws IOException {
        int read = in.read();
        if(read < 0)
            throw new IOException("Reached end of Stream");
        return read != 0;
    }
}