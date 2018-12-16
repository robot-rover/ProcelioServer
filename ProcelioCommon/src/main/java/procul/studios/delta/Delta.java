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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final Build source;
    private final Build target;
    private final DeltaManifest manifest;
    private final List<IOException> exceptions;

    private void addException(IOException e) {
        synchronized (exceptions) {
            exceptions.add(e);
        }
    }

    public Delta(Path deltaBaseDirectory, Build source, Build target, ExecutorService exec) throws IOException {
        this.exceptions = new ArrayList<>();
        this.source = source;
        this.target = target;
        this.baseDirectory = deltaBaseDirectory.resolve(getFilename());
        this.manifest = new DeltaManifest(source.getVersion(), target.getVersion());
        generateDelta(exec);
    }

    public Delta(Path deltaBaseDirectory, Build source, Build target) throws IOException {
        this(deltaBaseDirectory, source, target, Executors.newSingleThreadExecutor());
    }

    private void addNewFile(Path path) {
        LOG.debug("Adding {}", path);
        try(DigestInputStream input = new DigestInputStream(new BufferedInputStream(Files.newInputStream(target.getBaseDirectory().resolve(path))), Hashing.getMessageDigest())) {
            Path output = baseDirectory.resolve(path);
            Files.createDirectories(output.getParent());
            Files.copy(input, output);
            addFileHash(input.getMessageDigest().digest(), path);
        } catch (IOException e) {
            addException(e);
        }
    }

    private void deleteOldFile(Path path) {
        LOG.debug("Deleting {}", path);
        manifest.delete.add(path.toString());
    }

    private void diffFile(Path path) {
        try {
            LOG.debug("Diffing {}", path);
            Path targetFile = target.getBaseDirectory().resolve(path);
            Path sourceFile = source.getBaseDirectory().resolve(path);
            Path patchFile = baseDirectory.resolve(path.toString() + ".patch");
            Files.createDirectories(patchFile.getParent());
            try (InputStream sourceStream = new BufferedInputStream(Files.newInputStream(sourceFile));
                 DigestInputStream targetStream = new DigestInputStream(new BufferedInputStream(Files.newInputStream(targetFile)), Hashing.getMessageDigest());
                 OutputStream patchStream = new BufferedOutputStream(Files.newOutputStream(patchFile))) {
                BytesUtil.writeInt((int) Files.size(targetFile), patchStream);
                int size = Math.toIntExact(Math.max(Files.size(targetFile), Files.size(sourceFile)));
                LOG.trace("Block size: {}, Old File: {}, New File: {}", size, Files.size(sourceFile), Files.size(targetFile));
                final int blockSize = 1024;
                for (int pos = 0; pos < size; pos += blockSize) {
                    int blockLength = Math.min(blockSize, size - pos);
                    LOG.trace("Block {}", pos / blockSize);
                    ByteBufferOutputStream blockPatchStream = new ByteBufferOutputStream();
                    byte[] sourceBlock = new byte[blockLength];
                    sourceStream.read(sourceBlock);
                    byte[] targetBlock = new byte[blockLength];
                    targetStream.read(targetBlock);
                    try {
                        Diff.diff(sourceBlock, targetBlock, blockPatchStream);
                        LOG.trace("Patch ln {}", blockPatchStream.getCount());
                        BytesUtil.writeInt(blockPatchStream.getCount(), patchStream);
                        patchStream.write(blockPatchStream.getBuf(), 0, blockPatchStream.getCount());
                    } catch (InvalidHeaderException | CompressorException e) {
                        throw new IOException(e);
                    }
                }
                addFileHash(targetStream.getMessageDigest().digest(), path);
            }
        } catch (IOException e) {
            addException(e);
        }
    }

    private void addFileHash(byte[] hash, Path relativePath) {
        synchronized (manifest) {
            manifest.hashes.add(Hashing.printHexBinary(hash) + ":" + relativePath);
        }
    }

    private void generateDelta(ExecutorService executor) throws IOException {
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

        List<Callable<Object>> todo = new ArrayList<>();

        for (Path path : targetOnly)
            todo.add(Executors.callable(() -> addNewFile(path)));
        for (Path path : sourceOnly)
            deleteOldFile(path);
        for (Path path : sourceAndTarget)
            todo.add(Executors.callable(() -> diffFile(path)));
        try {
            executor.invokeAll(todo);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
        if(!exceptions.isEmpty())
            throw exceptions.get(0);

        Path manifestPath = baseDirectory.resolve("manifest.json");
        Files.write(manifestPath, gson.toJson(manifest).getBytes(StandardCharsets.UTF_8));
    }

    public String getFilename() {
        return "delta-" + source.getVersion() + "-" + target.getVersion();
    }

    @Override
    public String toString() {
        return "Delta " + source.getVersion() + " -> " + target.getVersion();
    }
}
