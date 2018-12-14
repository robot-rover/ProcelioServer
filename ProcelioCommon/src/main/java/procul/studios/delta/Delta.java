package procul.studios.delta;

import io.sigpipe.jbsdiff.Diff;
import io.sigpipe.jbsdiff.InvalidHeaderException;
import org.apache.commons.compress.compressors.CompressorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.util.ByteBufferOutputStream;
import procul.studios.util.BytesUtil;
import procul.studios.util.Hashing;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static procul.studios.util.GsonSerialize.gson;

/**
 * Represents a delta that can be applied to a versioned file tree to change its version
 * can create binary patches to be applied with {@link Build}
 */
public class Delta {
    private static final Logger LOG = LoggerFactory.getLogger(Delta.class);

    public static final Pattern pattern = Pattern.compile("delta-(\\d+)\\.(\\d+)\\.(\\d+)-(\\d+)\\.(\\d+)\\.(\\d+)");

    public Path getBaseDirectory() {
        return baseDirectory;
    }

    private final Path baseDirectory;
    final Build source;
    final Build target;
    DeltaManifest manifest;

    public Delta(Path deltaBaseDirectory, Build source, Build target) throws IOException {
        this.source = source;
        this.target = target;
        this.baseDirectory = deltaBaseDirectory.resolve(getFilename());
        this.manifest = new DeltaManifest(source.getVersion(), target.getVersion());
        generateDelta();
    }

    private void addNewFile(Path path) throws IOException {
        LOG.debug("Adding {}", path);
        try(DigestInputStream input = new DigestInputStream(new BufferedInputStream(Files.newInputStream(target.getBaseDirectory().resolve(path))), Hashing.getMessageDigest())) {
            Path output = baseDirectory.resolve(path);
            Files.createDirectories(output.getParent());
            Files.copy(input, output);
            addFileHash(input.getMessageDigest().digest(), path);
        }
    }

    private void deleteOldFile(Path path) {
        LOG.debug("Deleting {}", path);
        manifest.delete.add(path.toString());
    }

    private void diffFile(Path path) throws IOException {
        LOG.debug("Diffing {}", path);
        Path targetFile = target.getBaseDirectory().resolve(path);
        Path sourceFile = source.getBaseDirectory().resolve(path);
        Path patchFile = baseDirectory.resolve(path.toString() + ".patch");
        Files.createDirectories(patchFile.getParent());
        try(DigestInputStream targetStream = new DigestInputStream(new BufferedInputStream(Files.newInputStream(targetFile)), Hashing.getMessageDigest());
            InputStream sourceStream = new BufferedInputStream(Files.newInputStream(sourceFile));
            OutputStream patchStream = new BufferedOutputStream(Files.newOutputStream(patchFile))) {
            BytesUtil.writeInt((int) Files.size(targetFile), patchStream);
            int size = Math.toIntExact(Math.max(Files.size(targetFile), Files.size(sourceFile)));
            LOG.trace("Block size: {}, Old File: {}, New File: {}", size, Files.size(sourceFile), Files.size(targetFile));
            final int blockSize = 1024*1024;
            for (int pos = 0; pos < size; pos += blockSize) {
                int length = Math.min(blockSize, size - pos);
                LOG.trace("Block {}", pos / blockSize);
                ByteBufferOutputStream patchBlockStream = new ByteBufferOutputStream();
                byte[] sourceBlock = new byte[length];
                sourceStream.read(sourceBlock);
                byte[] targetBlock = new byte[length];
                targetStream.read(targetBlock);
                try {
                    Diff.diff(sourceBlock, targetBlock, patchBlockStream);
                    LOG.trace("Patch ln {}", patchBlockStream.getCount());
                    BytesUtil.writeInt(patchBlockStream.getCount(), patchStream);
                    patchStream.write(patchBlockStream.getBuf(), 0, patchBlockStream.getCount());
                } catch (InvalidHeaderException | CompressorException e) {
                    throw new IOException(e);
                }
            }
            addFileHash(targetStream.getMessageDigest().digest(), path);
        }
    }

    private void addFileHash(byte[] hash, Path relativePath) {
        manifest.hashes.add(Hashing.printHexBinary(hash) + ":" + relativePath);
    }

    private void generateDelta() throws IOException {
        manifest.newExec = target.manifest.exec;

        if(!Files.exists(baseDirectory)) {
            Files.createDirectory(baseDirectory);
        } else {
            LOG.info("{} already exists, skipping", this);
            return;
        }

        LOG.info("Creating {}", this);
        Set<Path> sourceOnly = new HashSet<>();
        Set<Path> sourceAndTarget = new HashSet<>();
        Set<Path> targetOnly = new HashSet<>(target.getFileList());
        for (Path path : source.getFileList()) {
            if (targetOnly.remove(path))
                sourceAndTarget.add(path);
            else
                sourceOnly.add(path);
        }

        for (Path path : targetOnly)
            addNewFile(path);
        for (Path path : sourceOnly)
            deleteOldFile(path);
        for (Path path : sourceAndTarget)
            diffFile(path);

        Path manifestPath = baseDirectory.resolve("manifest.json");
        Files.write(manifestPath, gson.toJson(manifest).getBytes(StandardCharsets.UTF_8));
//        gson.toJson(manifest, Files.newBufferedWriter(manifestPath));
    }

    public String getFilename() {
        return "delta-" + source.getVersion() + "-" + target.getVersion();
    }

    @Override
    public String toString() {
        return "Delta " + source.getVersion() + " -> " + target.getVersion();
    }
}
