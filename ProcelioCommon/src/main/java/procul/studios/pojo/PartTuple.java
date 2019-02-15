package procul.studios.pojo;

import procul.studios.util.BytesUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

public class PartTuple {
    public static final int SERIALIZER_VERSION = 1;
    // Vector(X, Y, Z)
    public byte[] transform;
    public byte rotation;
    public byte[] color;
    public short partId;

    public PartTuple(byte[] transform, byte rotation, byte[] color, short partId) {
        this.transform = transform;
        this.rotation = rotation;
        this.color = color;
        this.partId = partId;
    }

    public PartTuple(InputStream in, int version) throws IOException {
        if(version == 1) {
            transform = new byte[3];
            BytesUtil.readBytes(in, transform);
            rotation = BytesUtil.readByte(in);
            color = new byte[3];
            BytesUtil.readBytes(in, color);
            partId = BytesUtil.readShort(in);

        } else {
            throw new UnsupportedEncodingException("Version " + version + "not supported");
        }
    }

    public void serialize(OutputStream out) throws IOException {
        out.write(transform);
        BytesUtil.writeByte(out, rotation);
        out.write(color);
        BytesUtil.writeShort(out, partId);
    }

    @Override
    public String toString() {
        return "ID: " + partId + " @ " + formatTuple(transform) + " | rot: 0x" + Integer.toHexString(rotation & 0xFF) + " | col: " + formatTuple(color);
    }

    private String formatTuple(byte[] tuple) {
        return "(" + (tuple[0] & 0xFF) + ", " + (tuple[1] & 0xFF) + ", " + (tuple[2] & 0xFF) + ")";
    }
}