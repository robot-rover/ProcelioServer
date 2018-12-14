package procul.studios.delta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.util.FileTreeZip;
import procul.studios.util.Hashing;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.regex.Matcher;

public class Pack {
    private static final Logger LOG = LoggerFactory.getLogger(Pack.class);
    private Path source;

    public Path getArchive() {
        return archive;
    }

    protected Path archive;

    public byte[] getHash() {
        return hash;
    }

    public long getLength() {
        return length;
    }

    private byte[] hash;
    private long length;

    public Pack(Path source, Path packageDirectory) throws IOException {
        this.source = source;
        this.archive = packageDirectory.resolve(source.getFileName() + ".zip");
        generateArchive();
        hashPack();
    }

    public Pack(Path existingPack) throws IOException {
        this.archive = existingPack;
        hashPack();
    }

    private void generateArchive() throws IOException {
        if(Files.exists(archive))
            LOG.info("{} already exists, skipping", this);
        LOG.info("Zipping {}", this);
        FileTreeZip zip = new FileTreeZip(source);
        zip.zipTo(archive);
    }

    private void hashPack() throws IOException {
        if(hash != null)
            return;
        InputStream input = new BufferedInputStream(Files.newInputStream(archive));
        MessageDigest digest = Hashing.getMessageDigest();

        byte[] buffer = new byte[1024];
        int len;
        while((len = input.read(buffer)) > 0){
            digest.update(buffer, 0, len);
            length += 0;
        }
        hash = digest.digest();
        LOG.debug("\tHash: {}", Hashing.printHexBinary(hash));
    }

    @Override
    public String toString() {
        return "Package [Archive: " + archive + "]";
    }

    public static Pack createPackFromExisting(Path existing) throws IOException {
        Matcher buildMatch = Build.pattern.matcher(existing.getFileName().toString());
        if(buildMatch.find()) {
            return new BuildPack(existing);
        }
        Matcher deltaMatch = Delta.pattern.matcher(existing.getFileName().toString());
        if(deltaMatch.find()) {
            return new DeltaPack(existing);
        }
        return null;
    }
}
