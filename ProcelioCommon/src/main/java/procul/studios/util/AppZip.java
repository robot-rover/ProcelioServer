package procul.studios.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class AppZip {
    private static final Logger LOG = LoggerFactory.getLogger(AppZip.class);
    List<Path> fileList;
    File source;

    private Path pathRelativeTo(Path file, Path relativeTo){
        return file.subpath(relativeTo.getNameCount(), file.getNameCount());
    }


    public AppZip(File source){
        fileList = new ArrayList<>();
        this.source = source;
    }

    public static void zipit(File sourceFolder, File zipFile) throws IOException {
        new AppZip(sourceFolder).zipIt(zipFile);
    }

    /**
     * Zip it
     * @param zipFile output ZIP file location
     */
    public void zipIt(File zipFile) throws IOException {
        generateFileList(source);
        Path manifest = Paths.get("manifest.json");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile))) {
            LOG.debug("Creating Zip Archive: {}", zipFile.getAbsolutePath());
            out.setMethod(ZipEntry.DEFLATED);
            //Add Manifest First
            if(fileList.remove(manifest)){
                LOG.debug("Adding File: {}", manifest.toString());
                ZipEntry entry = new ZipEntry(manifest.toString());
                out.putNextEntry(entry);

                try (FileInputStream in = new FileInputStream(source.toPath().resolve(manifest).toFile())) {
                    copyLarge(in, out);
                }
            }
            for(Path file : this.fileList){
                LOG.debug("Adding File: {}", file.toString());
                ZipEntry entry = new ZipEntry(file.toString());
                out.putNextEntry(entry);

                try (FileInputStream in = new FileInputStream(source.toPath().resolve(file).toFile())) {
                    //todo: useCopyLarge for large streams
                    copyLarge(in, out);
                }
            }

            out.closeEntry();
        }
    }

    /**
     * Traverse a directory and get all files,
     * and add the file into fileList
     * @param node file or directory
     */
    private void generateFileList(File node){

        //add file only
        if(node.isFile()){
            fileList.add(generateZipEntry(node));
        }

        if(node.isDirectory()){
            for(File subnode : node.listFiles()){
                generateFileList(subnode);
            }
        }

    }

    /**
     * Format the file path for zip
     * @param file file path
     * @return Formatted file path
     */
    private Path generateZipEntry(File file){
        return pathRelativeTo(file.toPath(), source.toPath());
    }

    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

    /**
     * Copies bytes from a large (over 2GB) <code>InputStream</code> to an
     * <code>OutputStream</code>.
     * <p>
     * This method uses the provided buffer, so there is no need to use a
     * <code>BufferedInputStream</code>.
     * <p>
     *
     * @param input the <code>InputStream</code> to read from
     * @param output the <code>OutputStream</code> to write to
     * @return the number of bytes copied
     * @throws NullPointerException if the input or output is null
     * @throws IOException if an I/O error occurs
     * @since Commons IO 2.2
     */
    public static long copyLarge(final InputStream input, final OutputStream output)
            throws IOException {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        long count = 0;
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }
}