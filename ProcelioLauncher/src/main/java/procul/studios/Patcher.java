package procul.studios;

import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.Patch;
import org.apache.commons.compress.compressors.CompressorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.pojo.BuildManifest;
import procul.studios.pojo.PackageManifest;
import procul.studios.pojo.response.LauncherDownload;
import procul.studios.util.FileUtils;
import procul.studios.util.HashMismatchException;
import procul.studios.util.Hashing;
import procul.studios.util.Version;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static procul.studios.EndpointWrapper.gson;
import static procul.studios.ProcelioLauncher.backendEndpoint;
import static procul.studios.ProcelioLauncher.gameDir;

public class Patcher {

    EndpointWrapper wrapper;
    Consumer<Boolean> updateVisibleCallback;
    Consumer<Double> updateProgressCallback;
    Consumer<String> updateStatusCallback;

    public Patcher(EndpointWrapper wrapper, Consumer<Boolean> updateVisibleCallback, Consumer<Double> updateProgressCallback, Consumer<String> updateStatusCallback) {
        this.wrapper = wrapper;
        this.updateVisibleCallback = updateVisibleCallback;
        this.updateProgressCallback = updateProgressCallback;
        this.updateStatusCallback = updateStatusCallback;
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
                applyPatch(input, manifest);
            }
        } catch (IOException | HashMismatchException e) {
            updateVisibleCallback.accept(false);
            throw e;
        }
        updateVisibleCallback.accept(false);
        return loadManifest();
    }

    public void applyPatch(InputStream patch, BuildManifest manifest) throws IOException {
        LOG.info("Available bytes: " + patch.available());
        byte[] buffer = new byte[1024];
        PackageManifest packageManifest;
        try (ZipInputStream zipStream = new ZipInputStream(patch)) {
            ZipEntry entry = zipStream.getNextEntry();
            if (!entry.getName().equals("manifest.json"))
                throw new RuntimeException("manifest.json must be the first zip entry");
            try (ByteArrayOutputStream manifestData = new ByteArrayOutputStream()) {
                readEntry(zipStream, buffer, manifestData);
                packageManifest = gson.fromJson(new String(manifestData.toByteArray()), PackageManifest.class);
                if (packageManifest.delete == null)
                    packageManifest.delete = new ArrayList<>();
            }
            updateVisibleCallback.accept(true);
            updateStatusCallback.accept("Patching " + new Version(packageManifest.fromVersion) + " -> " + new Version(packageManifest.toVersion));
            LOG.info("Applying patch " + Arrays.toString(packageManifest.fromVersion) + " -> " + Arrays.toString(packageManifest.toVersion));
            while ((entry = zipStream.getNextEntry()) != null) {
                String fileName = entry.getName();
                if (fileName == null) {
                    LOG.warn("Null Entry Name");
                    continue;
                }
                if (FileUtils.getFileExtension(fileName).equals("patch")) {
                    String gameDirFile = fileName.substring(0, fileName.length() - ".patch".length());
                    File toPatch = new File(gameDir, gameDirFile);
                    if (!toPatch.exists()) {
                        LOG.warn("File is missing {}", toPatch.getAbsolutePath());
                        continue;
                    }
                    byte[] oldBytes = Files.readAllBytes(gameDir.toPath().resolve(gameDirFile));
                    try (ByteArrayOutputStream patchStream = new ByteArrayOutputStream();
                         OutputStream patchedOut = new FileOutputStream(toPatch, false)) {
                        readEntry(zipStream, buffer, patchStream);
                        Patch.patch(oldBytes, patchStream.toByteArray(), patchedOut);
                        if (toPatch.length() == 0) {
                            LOG.warn("File {} is now 0 bytes long: {}", toPatch.getAbsolutePath(), fileName);
                        }
                    } catch (InvalidHeaderException | CompressorException e) {
                        LOG.error("Patch Error", e);
                    }
                } else {
                    File newFile = new File(gameDir, fileName);
                    new File(newFile.getParent()).mkdirs();
                    if (entry.isDirectory())
                        continue;
                    try (OutputStream out = new FileOutputStream(newFile)) {
                        readEntry(zipStream, buffer, out);
                    }
                }
            }
            manifest.ignore = packageManifest.ignore;
            manifest.version = packageManifest.toVersion;
            manifest.exec = packageManifest.newExec;
            Files.write(gameDir.toPath().resolve("manifest.json"), gson.toJson(manifest).getBytes(), StandardOpenOption.TRUNCATE_EXISTING);

            for (String toDeletePath : packageManifest.delete) {
                File toDelete = new File(gameDir, toDeletePath);
                LOG.info("Deleting {}", toDeletePath);
                if (toDelete.exists() && toDelete.isDirectory())
                    FileUtils.deleteRecursive(toDelete);
                if (toDelete.exists() && !toDelete.delete())
                    LOG.warn("Cannot delete file {}", toDelete.getAbsolutePath());

            }

            for (String hashAndFile : packageManifest.filesAndHashes) {
                String hash = hashAndFile.substring(0, 32);
                String file = hashAndFile.substring(32, hashAndFile.length());
                MessageDigest hasher = Hashing.getMessageDigest();
                if (hasher == null) return;
                try (DigestInputStream digest = new DigestInputStream(new FileInputStream(new File(gameDir, file)), hasher)) {
                    while (digest.read(buffer) != -1) {}
                }
                String fileHash = Hashing.printHexBinary(hasher.digest());
                if (!hash.equals(fileHash)) {
                    LOG.info("Hashes for file {} do not match. Manifest - {}, File - {}", new File(gameDir, file), hash, fileHash);
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
        try (InputStreamReader isr = new InputStreamReader(new FileInputStream(new File(gameDir, "manifest.json")))) {
            manifest = EndpointWrapper.gson.fromJson(isr, BuildManifest.class);
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
        if (!gameDir.exists())
            gameDir.mkdir();
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
        BuildManifest manifest = null;
        try (InputStreamReader isr = new InputStreamReader(new FileInputStream(new File(gameDir, "manifest.json")))) {
            manifest = EndpointWrapper.gson.fromJson(isr, BuildManifest.class);
        }
        return manifest;
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
