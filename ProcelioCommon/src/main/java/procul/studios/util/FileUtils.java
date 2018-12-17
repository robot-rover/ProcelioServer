package procul.studios.util;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class FileUtils {
    private FileUtils() { }

    public static void deleteRecursive(Path dir) throws IOException {
        for(Path f : Files.newDirectoryStream(dir)){
            if(Files.isDirectory(f))
                deleteRecursive(f);
            Files.delete(f);
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

    public static void extractInputstream(InputStream stream, Path targetDir) throws IOException {
        byte[] buffer = new byte[1024];
        try (ZipInputStream zipStream = new ZipInputStream(stream)) {
            ZipEntry entry = null;
            while ((entry = zipStream.getNextEntry()) != null) {
                String fileName = entry.getName();
                Path newFile = targetDir.resolve(fileName);
                Files.createDirectories(newFile.getParent());
                try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(newFile))) {
                    int len;
                    while ((len = zipStream.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }
            }
        }
    }

    public static void copyRecursive(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new CopyFileVisitor(target));
    }

    static class CopyFileVisitor extends SimpleFileVisitor<Path> {
        private final Path targetPath;
        private Path sourcePath = null;
        public CopyFileVisitor(Path targetPath) {
            this.targetPath = targetPath;
        }

        @Override
        public FileVisitResult preVisitDirectory(final Path dir,
                                                 final BasicFileAttributes attrs) throws IOException {
            if (sourcePath == null) {
                sourcePath = dir;
            } else {
                Files.createDirectories(targetPath.resolve(sourcePath
                        .relativize(dir)));
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(final Path file,
                                         final BasicFileAttributes attrs) throws IOException {
            Files.copy(file,
                    targetPath.resolve(sourcePath.relativize(file)));
            return FileVisitResult.CONTINUE;
        }
    }

    public static String getSize(Path file) throws IOException {
        long length = Files.size(file);
        if(length < 1024)
            return length + " b";
        length /= 1024;
        if(length < 1024)
            return length + " kb";
        length /= 1024;
        if(length < 1024)
            return length + " mb";
        length /= 1024;
        return length + " gb";
    }
}
