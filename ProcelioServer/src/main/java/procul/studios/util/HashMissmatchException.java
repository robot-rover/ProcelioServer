package procul.studios.util;

public class HashMissmatchException extends Exception {
    public HashMissmatchException(String file){
        super("Unable to download " + file + " correctly");
    }

    public HashMissmatchException(String file, String expected, String actual){
        super("Unable to download " + file + " correctly ");
    }
}
