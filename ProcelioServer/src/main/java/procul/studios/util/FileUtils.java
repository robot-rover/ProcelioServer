package procul.studios.util;

import java.io.File;
import java.nio.file.Path;

public class FileUtils {
    public static Path pathRelativeTo(Path file, Path relativeTo){
        return file.subpath(relativeTo.getNameCount(), file.getNameCount());
    }

    public static void deleteRecursive(File dir){
        for(File f : dir.listFiles()){
            if(f.isDirectory())
                deleteRecursive(f);
            f.delete();
        }
    }

    public static String getFileExtension(String fileName) {
        String extension = "";

        int i = fileName.lastIndexOf('.');
        int p = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));

        if (i > p) {
            extension = fileName.substring(i+1);
        }
        return extension;
    }
}
