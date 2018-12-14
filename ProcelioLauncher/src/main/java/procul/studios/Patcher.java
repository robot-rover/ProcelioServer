package procul.studios;

import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.Patch;
import org.apache.commons.compress.compressors.CompressorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.delta.Build;
import procul.studios.delta.BuildManifest;
import procul.studios.delta.DeltaManifest;
import procul.studios.pojo.response.LauncherDownload;
import procul.studios.util.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static procul.studios.ProcelioLauncher.backendEndpoint;
import static procul.studios.util.GsonSerialize.gson;

public class Patcher {

    EndpointWrapper wrapper;
    Build currentBuild;
    Consumer<Boolean> updateVisibleCallback;
    Consumer<Double> updateProgressCallback;
    Consumer<String> updateStatusCallback;
    Path gameDir;

    public Patcher(Path gameDir, EndpointWrapper wrapper, Consumer<Boolean> updateVisibleCallback, Consumer<Double> updateProgressCallback, Consumer<String> updateStatusCallback) throws IOException {
        this.wrapper = wrapper;
        this.updateVisibleCallback = updateVisibleCallback;
        this.updateProgressCallback = updateProgressCallback;
        this.updateStatusCallback = updateStatusCallback;
        this.gameDir = gameDir;
        currentBuild = new Build(gameDir);
    }

    private static final Logger LOG = LoggerFactory.getLogger(Patcher.class);

    /**
     * Attempt to patch the game
     * @param manifest the build manifest of the current version
     * @throws IOException if unable to contact backend server
     * @throws HashMismatchException if a file was downloaded twice and was corrupted both times
     */
    public BuildManifest updateBuild(BuildManifest manifest) throws IOException, HashMismatchException {
        LauncherDownload gameStatus = wrapper.checkForUpdates(new Version(manifest.version));

        // if the versions match, the game is up to date
        if (gameStatus.upToDate) {
            LOG.info("All up to date");
            return loadManifest();
        }

        // if no patch path is available, only option is a fresh build
        if (gameStatus.patches == null) {
            return freshBuild();
        }

        // All set to patch
        LOG.info("Patching Build");
        updateVisibleCallback.accept(true);
        try {
            for (String patch : gameStatus.patches) {
                updateStatusCallback.accept("Downloading Patch " + patch);
                InputStream input = wrapper.getFile(backendEndpoint + patch, updateProgressCallback);
                applyDelta(input, manifest);
            }
        } catch (IOException | HashMismatchException e) {
            updateVisibleCallback.accept(false);
            throw e;
        }
        updateVisibleCallback.accept(false);
        return loadManifest();
    }

    public void applyDelta(InputStream delta, BuildManifest manifest) throws IOException {
        LOG.info("Available patch bytes: " + delta.available());
        byte[] buffer = new byte[1024];
        DeltaManifest packageManifest;
        try (ZipInputStream zipStream = new ZipInputStream(delta)) {
            ZipEntry entry = zipStream.getNextEntry();
            if (!entry.getName().equals("manifest.json"))
                throw new RuntimeException("manifest.json must be the first zip entry, not " + entry.getName());
            packageManifest = gson.fromJson(new InputStreamReader(zipStream), DeltaManifest.class);
            if (packageManifest.delete == null)
                packageManifest.delete = new ArrayList<>();
            updateVisibleCallback.accept(true);
            updateStatusCallback.accept("Patching " + new Version(packageManifest.source) + " -> " + new Version(packageManifest.target));
            LOG.info("Applying patch " + Arrays.toString(packageManifest.source) + " -> " + Arrays.toString(packageManifest.target));
            while ((entry = zipStream.getNextEntry()) != null) {
                String fileName = entry.getName();
                if (fileName == null) {
                    LOG.warn("Null Entry Name");
                    continue;
                }
                if (FileUtils.getFileExtension(fileName).equals("patch")) {
                    String newFileName = fileName.substring(0, fileName.length() - ".patch".length());
                    Path toPatch = gameDir.resolve(newFileName);
                    if (!Files.exists(toPatch)) {
                        LOG.warn("File is missing {}", toPatch);
                        continue;
                    }
                    byte[] oldFileBytes = Files.readAllBytes(toPatch);
                    try (OutputStream patchedOut = Files.newOutputStream(toPatch)) {
                        ByteBufferOutputStream patchStream = new ByteBufferOutputStream();
                        readEntry(zipStream, buffer, patchStream);
//                        Patch.patch(oldBytes, patchStream.toByteArray(), patchedOut);


                        int newFileLength = BytesUtil.readInt(new ByteArrayInputStream(patchStream.getBuf()));
                        byte[] patchBytes = new byte[patchStream.getCount()-4];
                        System.arraycopy(patchStream.getBuf(), 4, patchBytes, 0, patchBytes.length);
                        int size = Math.toIntExact(Math.max(oldFileBytes.length, newFileLength));
                        LOG.trace("Block size: {}, Old File: {}, New File: {}", size, oldFileBytes.length, newFileLength);
                        final int blockSize = 1024*1024;
                        byte[] oldBytes = new byte[size];
                        System.arraycopy(oldFileBytes, 0, oldBytes, 0, oldFileBytes.length);
                        int block = 0;
                        ByteBufferOutputStream patchBlockOut = new ByteBufferOutputStream();
                        for (int pos = 0; pos < patchBytes.length;) {
                            LOG.trace("Block {}", block);
                            int length = Math.min(blockSize, size - pos);
                            int patchLength = BytesUtil.readInt(new ByteArrayInputStream(patchBytes, pos, 4));
                            LOG.trace("Patch Length {}", patchLength);
                            byte[] oldBlockData = Arrays.copyOfRange(oldBytes, block * blockSize, (block + 1) * blockSize);
                            block++;
                            byte[] patchBlockData = Arrays.copyOfRange(patchBytes, pos + 4, pos + 4 + patchLength);
                            Patch.patch(oldBlockData, patchBlockData, patchBlockOut);
                            pos += 4 + patchLength;
                        }
                        patchedOut.write(patchBlockOut.getBuf(), 0, newFileLength);

                        if (Files.size(toPatch) == 0) {
                            LOG.warn("File {} is now 0 bytes long: {}", toPatch, fileName);
                        }
                    } catch (InvalidHeaderException | CompressorException e) {
                        LOG.error("Patch Error", e);
                    }
                } else {
                    Path newFile = gameDir.resolve(fileName);
                    Files.createDirectories(newFile.getParent());
                    if (entry.isDirectory())
                        continue;
                    try (OutputStream out = Files.newOutputStream(newFile)) {
                        readEntry(zipStream, buffer, out);
                    }
                }
            }
            manifest.version = packageManifest.target;
            manifest.exec = packageManifest.newExec;
            Files.write(gameDir.resolve("manifest.json"), gson.toJson(manifest).getBytes(), StandardOpenOption.TRUNCATE_EXISTING);

            for (String toDeletePath : packageManifest.delete) {
                Path toDelete = gameDir.resolve(toDeletePath);
                LOG.info("Deleting {}", toDeletePath);
                if (Files.isDirectory(toDelete))
                    FileUtils.deleteRecursive(toDelete);
                if (Files.exists(toDelete))
                    Files.delete(toDelete);

            }

            for (String hashAndFile : packageManifest.hashes) {
                String hash = hashAndFile.substring(0, 32);
                String file = hashAndFile.substring(33);
                MessageDigest hasher = Hashing.getMessageDigest();
                if (hasher == null) return;
                try (DigestInputStream digest = new DigestInputStream(Files.newInputStream(gameDir.resolve(file)), hasher)) {
                    while (digest.read(buffer) != -1) {}
                }
                String fileHash = Hashing.printHexBinary(hasher.digest());
                if (!hash.equals(fileHash)) {
                    LOG.info("Hashes for file {} do not match. Manifest - {}, File - {}", gameDir.resolve(file), hash, fileHash);
                }
            }
        }  catch (IOException e) {
            updateVisibleCallback.accept(false);
            throw e;
        }
        updateVisibleCallback.accept(false);
    }

    public BuildManifest loadManifest() throws FileNotFoundException, IOException {
        BuildManifest manifest = null;
        try (InputStreamReader isr = new InputStreamReader(Files.newInputStream(gameDir.resolve("manifest.json")))) {
            manifest = gson.fromJson(isr, BuildManifest.class);
        }
        return manifest;
    }

    private void readEntry(ZipInputStream zip, byte[] buffer, OutputStream out) throws IOException {
        int len;
        while ((len = zip.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
    }

    public BuildManifest freshBuild() throws IOException, HashMismatchException {

        LOG.info("Making fresh build");
        if (Files.exists(gameDir))
            Files.createDirectory(gameDir);
        else
            FileUtils.deleteRecursive(gameDir);
        updateVisibleCallback.accept(true);
        updateStatusCallback.accept("Downloading Build /launcher/build");
        InputStream hashes = null;
        try {
            hashes = wrapper.getFile(backendEndpoint + "/launcher/build", updateProgressCallback);
            FileUtils.extractInputstream(hashes, gameDir);
        } catch (IOException | HashMismatchException e) {
            updateVisibleCallback.accept(false);
            throw e;
        }

        updateVisibleCallback.accept(false);
        return loadManifest();
    }

    private static DecimalFormat df = new DecimalFormat("#.##");

    private static String formatDataSize(int bytes) {
        String[] extensions = {"B", "KB", "MB", "GB", "TB"};
        int extensionIndex = 0;
        double size = bytes;
        while (Math.abs(size / 1024) >= 1) {
            size /= 1024;
            extensionIndex++;
        }
        return df.format(size) + " " + extensions[extensionIndex];
    }
}
