package procul.studios.util;

import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class Hashing {
    public static MessageDigest getMessageDigest(){
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    public String previewByteArray(byte[] array) {
        return DatatypeConverter.printHexBinary(Arrays.copyOfRange(array, 0, Math.floorDiv(array.length, 20)));
    }
}
