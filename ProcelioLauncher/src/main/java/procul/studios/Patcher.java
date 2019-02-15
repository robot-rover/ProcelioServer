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
import java.nio.file.Paths;
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
import static procul.studios.gson.GsonSerialize.gson;

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
        try {
            currentBuild = new Build(gameDir);
        } catch (IOException e) {
            currentBuild = null;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(Patcher.class);

    /**
     * Attempt to patch the game
     * @throws IOException if unable to contact backend server
     * @throws HashMismatchException if a file was downloaded twice and was corrupted both times
     */
    public Build updateBuild() throws IOException, HashMismatchException {
        LauncherDownload gameStatus = wrapper.checkForUpdates(currentBuild.getVersion());

        // if the versions match, the game is up to date
        if (gameStatus.upToDate) {
            LOG.info("All up to date");
            return currentBuild;
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
                applyDelta(input);
            }
        } catch (IOException | HashMismatchException e) {
            updateVisibleCallback.accept(false);
            throw e;
        }
        updateVisibleCallback.accept(false);
        return currentBuild;
    }

    public void applyDelta(InputStream delta) throws IOException {
        LOG.info("Available patch bytes: " + delta.available());
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
                    Path sourcePath = Paths.get(toPatch.toString() + ".old" + System.currentTimeMillis());
                    Files.move(toPatch, sourcePath);
                    try(InputStream sourceStream = Files.newInputStream(sourcePath);
                        OutputStream patchedOut = Files.newOutputStream(toPatch)) {
                        ByteBufferOutputStream readPatchStream = new ByteBufferOutputStream();
                        readEntry(zipStream, readPatchStream);

                        ByteArrayInputStream patchStream = new ByteArrayInputStream(readPatchStream.getBuf(), 0 , readPatchStream.getCount());
                        int blockSize = BytesUtil.readInt(patchStream);
                        LOG.trace("Patching {}", newFileName);
                        byte[] buffer = new byte[blockSize];
                        byte[] patchBlockLengthBuffer = new byte[4];
                        while (true) {
                            int isPatchesRemaining = patchStream.read(patchBlockLengthBuffer);
                            if(isPatchesRemaining < 1)
                                break;
                            int patchBlockLength = BytesUtil.readInt(patchBlockLengthBuffer);
                            LOG.trace("Patch Length: {}", patchBlockLength);
                            int sourceBytesRead = sourceStream.read(buffer);
                            byte[] oldBlockData = Arrays.copyOfRange(buffer, 0, Math.max(sourceBytesRead, 0));
                            if(patchBlockLength == -1) {
                                patchedOut.write(oldBlockData);
                                LOG.trace("Writing block from source");
                            } else {
                                byte[] patchBlockData = new byte[patchBlockLength];
                                patchStream.read(patchBlockData);
                                Patch.patch(oldBlockData, patchBlockData, patchedOut);
                            }
                        }


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
                        readEntry(zipStream, out);
                    }
                }
            }
            BuildManifest manifest = currentBuild.getManifest();
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
                    while (digest.read(readEntryBuffer) != -1) {}
                }
                String fileHash = Hashing.printHexBinary(hasher.digest());
                if (!hash.equals(fileHash)) {
                    LOG.info("Hashes for file {} do not match. Manifest - {}, File - {}", gameDir.resolve(file), hash, fileHash);
                }
            }

            currentBuild = new Build(gameDir);
        }  catch (IOException e) {
            updateVisibleCallback.accept(false);
            throw e;
        }
        updateVisibleCallback.accept(false);
    }

    private static byte[] readEntryBuffer = new byte[1024];
    private void readEntry(ZipInputStream zip, OutputStream out) throws IOException {
        int len;
        while ((len = zip.read(readEntryBuffer)) > 0) {
            out.write(readEntryBuffer, 0, len);
        }
    }

    public Build freshBuild() throws IOException, HashMismatchException {
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
        currentBuild = new Build(gameDir);
        return currentBuild;
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
