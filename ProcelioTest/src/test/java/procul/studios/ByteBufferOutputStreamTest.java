package procul.studios;

import org.junit.Assert;
import org.junit.Test;
import procul.studios.util.ByteBufferOutputStream;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

public class ByteBufferOutputStreamTest {
    private static final Random r = new Random();
    @Test
    public void oneArrayWrite() throws IOException {
        byte[] data = randomData(100);
        ByteBufferOutputStream stream = new ByteBufferOutputStream();
        stream.write(data);
        Assert.assertArrayEquals(data, Arrays.copyOfRange(stream.getBuf(), 0, stream.getCount()));
    }

    @Test
    public void bytesWrite() {
        byte[] data = randomData(76);
        ByteBufferOutputStream stream = new ByteBufferOutputStream();
        for (byte datum : data) {
            stream.write(datum & 0xFF);
        }
        Assert.assertArrayEquals(data, Arrays.copyOfRange(stream.getBuf(), 0, stream.getCount()));
    }

    private byte[] randomData(int length) {
        byte[] data = new byte[length];
        r.nextBytes(data);
        return data;
    }

    @Test
    public void resetStream() throws IOException {
        byte[] data = randomData(113);
        ByteBufferOutputStream stream = new ByteBufferOutputStream();
        for (byte datum : data) {
            stream.write(datum & 0xFF);
        }
        Assert.assertArrayEquals(data, Arrays.copyOfRange(stream.getBuf(), 0, stream.getCount()));

        byte[] data2 = randomData(8);
        stream.reset();
        stream.write(data2);
        Assert.assertArrayEquals(data2, Arrays.copyOfRange(stream.getBuf(), 0, stream.getCount()));
    }
}
