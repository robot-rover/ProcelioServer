package procul.studios.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileTreeZip {
    private static final Logger LOG = LoggerFactory.getLogger(FileTreeZip.class);
    List<Path> fileList;
    Path source;


    public FileTreeZip(Path source){
        fileList = new ArrayList<>();
        this.source = source;
    }

    /**
     * Zip it
     * @param zipFile output ZIP file location
     */
    public void zipTo(Path zipFile) throws IOException {
        generateFileList(source);
        Path manifest = Paths.get("manifest.json");
        try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipFile)))) {
            LOG.debug("Creating Zip Archive: {}", zipFile);
            out.setMethod(ZipEntry.DEFLATED);
            //Add Manifest First
            if(fileList.remove(manifest)){
                addFileToArchive(manifest, out);
            }
            for(Path file : this.fileList){
                addFileToArchive(file, out);
            }
        }
    }

    private void addFileToArchive(Path path, ZipOutputStream out) throws IOException {
        LOG.debug("Adding File: {}", path);
        ZipEntry entry = new ZipEntry(path.toString());
        out.putNextEntry(entry);
        Files.copy(source.resolve(path), out);
        out.closeEntry();
    }

    /**
     * Traverse a directory and get all files,
     * and add the file into fileList
     * @param node file or directory
     */
    private void generateFileList(Path node) throws IOException {
        //add file only
        if(Files.isRegularFile(node)){
            fileList.add(source.relativize(node));
        }

        if(Files.isDirectory(node)){
            for(Path subnode : Files.newDirectoryStream(node)){
                generateFileList(subnode);
            }
        }

    }

}