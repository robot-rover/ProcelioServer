package procul.studios.tool;

import procul.studios.pojo.Inventory;
import procul.studios.pojo.Robot;
import procul.studios.pojo.StatFile;
import procul.studios.pojo.StatFileBinary;
import procul.studios.util.BytesUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Util {
    private Util() { }

    public static void printInfo(byte[] allBytes, StatFile config) throws IOException {
        if (BytesUtil.readInt(allBytes) == Inventory.MAGIC_NUMBER) {
            System.out.println(new Inventory(allBytes));
        } else if (BytesUtil.readInt(allBytes) == Robot.MAGIC_NUMBER) {
            System.out.println(new Robot(allBytes));
        } else if(BytesUtil.readInt(allBytes) == StatFileBinary.MAGIC_NUMBER) {
            System.out.println(new StatFileBinary(allBytes));
        } else {
            System.out.println("Binary File (Len: " + allBytes.length + ") type not recognized...");
        }
    }

    public static byte[] readAllBytes(InputStream is) throws IOException {
        List<byte[]> bufs = null;
        byte[] result = null;
        int total = 0;
        int remaining = Integer.MAX_VALUE;
        int n;
        do {
            byte[] buf = new byte[Math.min(remaining, 8192)];
            int nread = 0;

            // read to EOF which may read more or less than buffer size
            while ((n = is.read(buf, nread,
                    Math.min(buf.length - nread, remaining))) > 0) {
                nread += n;
                remaining -= n;
            }

            if (nread > 0) {
                if ((Integer.MAX_VALUE - 8) - total < nread) {
                    throw new OutOfMemoryError("Required array size too large");
                }
                total += nread;
                if (result == null) {
                    result = buf;
                } else {
                    if (bufs == null) {
                        bufs = new ArrayList<>();
                        bufs.add(result);
                    }
                    bufs.add(buf);
                }
            }
            // if the last call to read returned -1 or the number of bytes
            // requested have been read then break
        } while (n >= 0 && remaining > 0);

        if (bufs == null) {
            if (result == null) {
                return new byte[0];
            }
            return result.length == total ?
                    result : Arrays.copyOf(result, total);
        }

        result = new byte[total];
        int offset = 0;
        remaining = total;
        for (byte[] b : bufs) {
            int count = Math.min(b.length, remaining);
            System.arraycopy(b, 0, result, offset, count);
            offset += count;
            remaining -= count;
        }

        return result;
    }
}
