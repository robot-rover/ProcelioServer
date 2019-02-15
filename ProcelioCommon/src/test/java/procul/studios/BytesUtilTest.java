package procul.studios;

import org.junit.Assert;
import org.junit.Test;
import procul.studios.util.BytesUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class BytesUtilTest {

    @Test
    public void integerTest() throws IOException {
        int[] testInts = new int[]{0, -1, 14040, Integer.MAX_VALUE, Integer.MIN_VALUE, 0, -450, 870, 255, 256, 128, 127, 560, -1024};
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for(int testInt : testInts) {
            BytesUtil.writeInt(baos, testInt);
        }
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        int[] resultInts = new int[testInts.length];
        for(int i = 0; i < resultInts.length; i++) {
            resultInts[i] = BytesUtil.readInt(bais);
        }
        Assert.assertArrayEquals(testInts, resultInts);

        ByteArrayInputStream bais2 = new ByteArrayInputStream(baos.toByteArray());
        int[] resultInts2 = new int[testInts.length];
        byte[] buffer = new byte[4];
        for(int i = 0; i < resultInts2.length; i++) {
            bais2.read(buffer);
            System.out.println(Arrays.toString(buffer));
            for(byte b : buffer) {
                System.out.print(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0'));
            }
            System.out.println();
            resultInts2[i] = BytesUtil.readInt(buffer);
            System.out.println(Integer.toBinaryString(resultInts2[i]));
        }
        Assert.assertArrayEquals(testInts, resultInts2);
    }

    @Test
    public void longTest() throws IOException {
        long[] testLongs = new long[]{0, -1, 14040, Long.MAX_VALUE, Long.MIN_VALUE, -0, -450, 870, 255, 256, 128, 127, 560, -1024};
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for(long testLong : testLongs) {
            BytesUtil.writeLong(baos, testLong);
        }
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        long[] resultLongs = new long[testLongs.length];
        for(int i = 0; i < resultLongs.length; i++) {
            resultLongs[i] = BytesUtil.readLong(bais);
        }
        Assert.assertArrayEquals(testLongs, resultLongs);
    }

    @Test
    public void shortTest() throws IOException {
        short[] testShorts = new short[]{0, -1, 14040, Short.MAX_VALUE, Short.MIN_VALUE, -0, -450, 870, 255, 256, 128, 127, 560, -1024};
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for(short testShort : testShorts) {
            BytesUtil.writeShort(baos, testShort);
        }
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        short[] resultShorts = new short[testShorts.length];
        for(int i = 0; i < resultShorts.length; i++) {
            resultShorts[i] = BytesUtil.readShort(bais);
        }
        Assert.assertArrayEquals(testShorts, resultShorts);
    }

    @Test
    public void booleanTest() throws IOException {
        boolean[] testBools = new boolean[]{true, false, false, true, false};
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for(boolean testBool : testBools) {
            BytesUtil.writeBoolean(baos, testBool);
        }
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        boolean[] resultBools = new boolean[testBools.length];
        for(int i = 0; i < resultBools.length; i++) {
            resultBools[i] = BytesUtil.readBoolean(bais);
        }
        Assert.assertArrayEquals(testBools, resultBools);
    }

}
