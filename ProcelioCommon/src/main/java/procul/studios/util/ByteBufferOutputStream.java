package procul.studios.util;

import java.io.ByteArrayOutputStream;

public class ByteBufferOutputStream extends ByteArrayOutputStream {

    public int getCount() {
        return count;
    }

    public byte[] getBuf() {
        return buf;
    }
}
