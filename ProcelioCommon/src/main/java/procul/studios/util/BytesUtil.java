package procul.studios.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class BytesUtil {
    private BytesUtil() { }

    // Little Endian
    public static void writeInt(int i, OutputStream os) throws IOException {
        os.write(i & 0xFF);
        os.write((i >> 8) & 0xFF);
        os.write((i >> 16) & 0xFF);
        os.write((i >> 24) & 0xFF);
    }

    public static int readInt(InputStream is) throws IOException {
        int result = is.read();
        result |= (is.read() << 8);
        result |= (is.read() << 16);
        result |= (is.read() << 24);
        return result;
    }

    public static void writeLong(long l, OutputStream os) throws IOException {
        os.write((int) (l & 0xFF));
        os.write((int) ((l >> 8) & 0xFF));
        os.write((int) ((l >> 16) & 0xFF));
        os.write((int) ((l >> 24) & 0xFF));
        os.write((int) ((l >> 32) & 0xFF));
        os.write((int) ((l >> 40) & 0xFF));
        os.write((int) ((l >> 48) & 0xFF));
        os.write((int) ((l >> 56) & 0xFF));
    }

    public static long readLong(InputStream is) throws IOException {
        long result = is.read();
        result |= ((long) is.read() << 8);
        result |= ((long) is.read() << 16);
        result |= ((long) is.read() << 24);
        result |= ((long) is.read() << 32);
        result |= ((long) is.read() << 40);
        result |= ((long) is.read() << 48);
        result |= ((long) is.read() << 56);
        return result;
    }

    public static void writeFloat(float f, OutputStream os) throws IOException {
        writeInt(Float.floatToRawIntBits(f), os);
    }

    public static float readFloat(InputStream is) throws IOException {
        return Float.intBitsToFloat(readInt(is));
    }

    public static void writeDouble(double d, OutputStream os) throws IOException {
        writeLong(Double.doubleToRawLongBits(d), os);
    }

    public static double readDouble(InputStream is) throws IOException {
        return Double.longBitsToDouble(readLong(is));
    }

    public static void writeShort(short s, OutputStream os) throws IOException {
        os.write(s & 0xFF);
        os.write((s >> 8) & 0xFF);
    }

    public static short readShort(InputStream is) throws IOException {
        short result = (short) is.read();
        result |= (is.read() << 8);
        return result;
    }

    public static void writeBoolean(boolean b, OutputStream os) throws IOException {
        os.write(b ? 0x01 : 0x00);
    }

    public static boolean readBoolean(InputStream is) throws IOException {
        return is.read() != 0;
    }
}