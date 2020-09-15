package procul.studios.util;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class FileUtils {
    private FileUtils() {
    }

    public static void deleteRecursive(Path dir) throws IOException {
        var str = Files.newDirectoryStream(dir);
        for (Path f : str) {
            if (Files.isDirectory(f))
                deleteRecursive(f);
            Files.delete(f);
        }
        str.close();
    }

    // Return true iff delete succssful
    public static boolean deleteRecursive(File f, Set<File> blacklist) {
        if (!f.exists() || blacklist.contains(f))
            return true;
        if (!f.isDirectory()) {
            return f.delete();
        }
        File[] arr = f.listFiles();
        if (arr == null || arr.length == 0)
            return true;
        for (File f2 : arr) {
            f2.setWritable(true);
            if (!deleteRecursive(f2, blacklist))
                return false;
        }
        return true;
    }


    public static String getFileExtension(String fileName) {
        String extension = "";

        int i = fileName.lastIndexOf('.');
        int p = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));

        if (i > p) {
            extension = fileName.substring(i + 1);
        }
        return extension;
    }

    public static void extractInputstream(InputStream stream, Path targetDir) throws IOException {
        extractInputstream(stream, targetDir, null);
    }
    public static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }
    public static void extractInputstream(InputStream stream, Path targetDir, Consumer<Double> scrollingProgress) throws IOException {
        double perFile = 0.005;
        double stat = 0;
        byte[] buffer = new byte[1024];
        ZipInputStream zis = new ZipInputStream(stream);
        ZipEntry zipEntry = zis.getNextEntry();
        while (zipEntry != null) {
            if (zipEntry.isDirectory()) {
                zipEntry = zis.getNextEntry();
                continue;
            }
            stat += perFile;
            if (stat > 1) stat = 0;
            if (scrollingProgress != null)
                scrollingProgress.accept(stat);
            File newFile = targetDir.resolve(zipEntry.getName()).toFile();
            newFile.getParentFile().mkdirs();
            FileOutputStream fos = new FileOutputStream(newFile);
            int len;
            while ((len = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            zipEntry = zis.getNextEntry();
        }
        zis.closeEntry();
        zis.close();
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
        if (length < 1024)
            return length + " b";
        length /= 1024;
        if (length < 1024)
            return length + " kb";
        length /= 1024;
        if (length < 1024)
            return length + " mb";
        length /= 1024;
        return length + " gb";
    }
}
