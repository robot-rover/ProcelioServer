package procul.studios.util;

public class HashMismatchException extends Exception {
    public HashMismatchException(String file){
        super("Unable to download " + file + " correctly");
    }

    public HashMismatchException(String file, String expected, String actual){
        super("Unable to download " + file + " correctly. Expected - " + expected + ", Actual - " + actual);
    }
}
