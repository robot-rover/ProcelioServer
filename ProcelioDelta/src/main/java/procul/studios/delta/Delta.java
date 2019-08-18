package procul.studios.delta;

import io.sigpipe.jbsdiff.Diff;
import io.sigpipe.jbsdiff.InvalidHeaderException;
import org.apache.commons.compress.compressors.CompressorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.util.ByteBufferOutputStream;
import procul.studios.util.BytesUtil;
import procul.studios.util.FileUtils;
import procul.studios.util.Hashing;
import sais.SaisDiffSettings;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static procul.studios.gson.GsonSerialize.gson;

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
            LOG.info("Diffing {} | {}", path, FileUtils.getSize(target.getBaseDirectory().resolve(path)));
            Path targetFile = target.getBaseDirectory().resolve(path);
            Path sourceFile = source.getBaseDirectory().resolve(path);
            Path patchFile = baseDirectory.resolve(path.toString() + ".patch");
            Files.createDirectories(patchFile.getParent());
            try (InputStream sourceStream = new BufferedInputStream(Files.newInputStream(sourceFile));
                 DigestInputStream targetStream = new DigestInputStream(new BufferedInputStream(Files.newInputStream(targetFile)), Hashing.getMessageDigest());
                 OutputStream patchStream = new BufferedOutputStream(Files.newOutputStream(patchFile))) {
                final int blockSize = 1024*1024;
                BytesUtil.writeInt(patchStream, blockSize);
                int size = Math.toIntExact(Math.max(Files.size(targetFile), Files.size(sourceFile)));
                LOG.debug("Block size: {}, Old File: {}, New File: {}, Total Blocks: {}", size, Files.size(sourceFile), Files.size(targetFile), size/blockSize + 1);
                byte[] buffer = new byte[blockSize];
                for (int pos = 0; pos < size; pos += blockSize) {
                    int blockLength = Math.min(blockSize, size - pos);
                    LOG.trace("Block {}", pos / blockSize);
                    ByteBufferOutputStream blockPatchStream = new ByteBufferOutputStream();
                    int sourceBytesRead = sourceStream.read(buffer);
                    byte[] sourceBlock = Arrays.copyOfRange(buffer, 0, Math.max(sourceBytesRead, 0));
                    int targetBytesRead = targetStream.read(buffer);
                    if(targetBytesRead < 1)
                        break;
                    byte[] targetBlock = Arrays.copyOfRange(buffer, 0, Math.max(targetBytesRead, 0));
                    try {
                        if(Arrays.equals(sourceBlock, targetBlock)) {
                            LOG.trace("Block source and target is equal");
                            BytesUtil.writeInt(patchStream, -1);
                            continue;
                        }
                        Diff.diff(sourceBlock, targetBlock, blockPatchStream, new SaisDiffSettings());
                        LOG.trace("Block Length: {}, Patch Length: {}", blockLength, blockPatchStream.getCount());
                        BytesUtil.writeInt(patchStream, blockPatchStream.getCount());
                        patchStream.write(blockPatchStream.getBuf(), 0, blockPatchStream.getCount());
                        if(blockPatchStream.getCount() > blockLength) {
                            LOG.trace("Patch is longer than data");
                        }
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
        if(executor == null) {
            try {
                for(Callable<Object> call : todo)
                    call.call();
            } catch (Exception e) {
                throw new IOException(e);
            }
        } else try {
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
