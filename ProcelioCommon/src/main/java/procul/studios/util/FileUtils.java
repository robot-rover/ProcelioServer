package procul.studios.util;

import java.io.*;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileUtils {

    public static void deleteRecursive(File dir, List<Path> ignore){
        for(File f : dir.listFiles()){
            if(ignore != null && ignore.contains(f.toPath()))
                continue;
            if(f.isDirectory())
                deleteRecursive(f, ignore);
            f.delete();
        }
    }

    public static void deleteRecursive(File dir){
        deleteRecursive(dir, null);
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

    public static void extractInputstream(InputStream stream, File targetDir) throws IOException {
        byte[] buffer = new byte[1024];
        try (ZipInputStream zipStream = new ZipInputStream(stream)) {
            ZipEntry entry = null;
            while ((entry = zipStream.getNextEntry()) != null) {
                String fileName = entry.getName();
                File newFile = new File(targetDir, fileName);
                new File(newFile.getParent()).mkdirs();
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(newFile))) {
                    int len;
                    while ((len = zipStream.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }
            }
        }
    }
}
