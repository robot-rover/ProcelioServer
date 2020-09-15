package procul.studios;

import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.Patch;
import org.apache.commons.compress.compressors.CompressorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.delta.Build;
import procul.studios.delta.BuildManifest;
import procul.studios.delta.DeltaManifest;
import procul.studios.pojo.response.PatchList;
import procul.studios.util.*;

import java.io.*;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static procul.studios.ProcelioLauncher.backendEndpoint;
import static procul.studios.gson.GsonSerialize.gson;

public class Patcher {
    private static final String updateInfoFile = "__updatetimestamp";

    EndpointWrapper wrapper;
    Build currentBuild;
    Consumer<Boolean> updateVisibleCallback;
    Consumer<Double> updateProgressCallback;
    Consumer<String> updateStatusCallback;
    public Path gameDir;

    LauncherSettings settings;

    public Patcher(LauncherSettings settings, Path gameDir, EndpointWrapper wrapper, Consumer<Boolean> updateVisibleCallback, Consumer<Double> updateProgressCallback, Consumer<String> updateStatusCallback) throws IOException {
        this.wrapper = wrapper;
        this.updateVisibleCallback = updateVisibleCallback;
        this.updateProgressCallback = updateProgressCallback;
        this.updateStatusCallback = updateStatusCallback;
        this.settings = settings;
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
        PatchList gameStatus = wrapper.checkForUpdates(currentBuild.getVersion(), settings.useDevBuilds);
        // if the versions match, the game is up to date
        if (gameStatus.upToDate) {
            LOG.info("All up to date");
            return currentBuild;
        }

        if (wasInterrupted()) {
            updateVisibleCallback.accept(true);
            LOG.warn("Rolling back interrupted update");
            updateStatusCallback.accept("Rolling back interrupted update");

            rollbackAttempt();
            LOG.warn("Roll successful");
            updateStatusCallback.accept("Rollback successful. Re-attempting update...");

            try { Thread.sleep(1000); } catch (Exception ignored) { /* Purely for visibility */}
        }

        long newVersionSize = wrapper.getFileSize(backendEndpoint + "/game/build/" + gameStatus.update_to.toString());
        long oldVersionSize = wrapper.getFileSize(backendEndpoint + "/game/build/" + currentBuild.getVersion());
        String persistent = "After update, install will consume ";
        if (oldVersionSize == -1)
            persistent += formatDataSize(newVersionSize);
        else if (newVersionSize - oldVersionSize > 0)
            persistent += "an additional " + formatDataSize(newVersionSize-oldVersionSize) ;
        else
            persistent += formatDataSize(oldVersionSize - newVersionSize) + " less";
        persistent += " of disk space";
        String patchMsg = "Updating process will temporarily require " + formatDataSize((long)(1.5*newVersionSize)) + " of disk space";

        boolean memoryOK = FX.accept("Storage Use", persistent+"\n"+patchMsg).orElse(false);
        if (!memoryOK)
            throw new CancellationException();
        // if no patch path is available, only option is a fresh build
        if (gameStatus.patches == null || gameStatus.patches.length == 0) {
            return freshBuild(settings.useDevBuilds);
        }



        // All set to patch
        LOG.info("Patching Build");
        updateVisibleCallback.accept(true);
        try {
            for (String patch : gameStatus.patches) {
                updateStatusCallback.accept("Downloading Patch " + patch);
                InputStream input = wrapper.getFile(backendEndpoint + "/game/patch/" + patch, updateProgressCallback);
                if (!applyDelta(input))
                    return freshBuild(settings.useDevBuilds);
            }
        } catch (IOException | HashMismatchException e) {
            updateVisibleCallback.accept(false);
            throw e;
        }
        updateVisibleCallback.accept(false);
        return currentBuild;
    }

    /**
     * Return true if a previous attempt to patch was interrupted
     */
    public boolean wasInterrupted() {
        return gameDir.resolve(updateInfoFile).toFile().exists();
    }

    /**
     * Attempt to roll back the state of the directory: copy .old files back to the original name.
     * Intended for use if a patch was aborted midway through. Will not delete new files.
     * @throws IOException
     */
    public void rollbackAttempt() throws IOException {
        Path updateLog = gameDir.resolve(updateInfoFile);
        BufferedReader br = new BufferedReader(new FileReader(updateLog.toFile()));
        long timestamp;
        try {
            timestamp = Long.parseLong(br.readLine());
        } catch (NumberFormatException nfe) {
            LOG.error(nfe.toString());
            throw new IOException();
        }

        String suffix = ".old" + timestamp;

        rollbackDirectory(gameDir.toFile(), suffix);

        updateLog.toFile().delete();
    }

    private void rollbackDirectory(File f, String suffix) throws IOException {
        if (!f.isDirectory())
            return;
        File[] sub = f.listFiles();
        if (sub == null)
            return;

        for (int i = 0; i < sub.length; ++i) {
            if (!sub[i].getPath().endsWith(suffix)) {
                if (sub[i].isDirectory())
                    rollbackDirectory(sub[i], suffix);
                continue;
            }
            String newName = sub[i].getAbsolutePath();
            newName = newName.substring(0, newName.length() - suffix.length());
            Path newPath = Paths.get(newName);
            if (newPath.toFile().exists())
                newPath.toFile().delete();
            Files.move(sub[i].toPath(), newPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            rollbackDirectory(newPath.toFile(), suffix);
        }

    }

    /**
     * Eliminate all .old####### files from the folder, recursively
     * @param root The top-level directory to clear from
     */
    public void clearOldFiles(File root) {
        LauncherUtilities.deleteMatchingRecursive(root, ".*\\.old\\d+$");
    }

    public boolean applyDelta(InputStream delta) throws IOException {
        long timestamp =  System.currentTimeMillis();
        Path updateLog = gameDir.resolve(updateInfoFile);
        BufferedWriter bw = new BufferedWriter(new FileWriter(updateLog.toFile()));
        bw.write(Long.toString(timestamp));
        bw.close();

        LOG.info("Available patch bytes: " + delta.available());
        DeltaManifest packageManifest;
        boolean ok = true;
        try (ZipInputStream zipStream = new ZipInputStream(delta)) {
            ZipEntry entry = zipStream.getNextEntry();
            if (!entry.getName().equals("manifest.json"))
                throw new RuntimeException("manifest.json must be the first zip entry, not " + entry.getName());
            packageManifest = gson.fromJson(new InputStreamReader(zipStream), DeltaManifest.class);
            if (packageManifest.delete == null)
                packageManifest.delete = new ArrayList<>();
            updateVisibleCallback.accept(true);
            updateStatusCallback.accept("Patching " + packageManifest.source + " -> " + packageManifest.target);
            LOG.info("Applying patch " + packageManifest.source + " -> " + packageManifest.target);
            updateProgressCallback.accept(0.0);
            double offset = 0.01;
            double val = 0;
            while ((entry = zipStream.getNextEntry()) != null) {
                LOG.info(entry.getName());
                if (entry.isDirectory()) {
                    continue;
                }
                val += offset; if (val > 1) val = 0;
                updateProgressCallback.accept(val);
                String fileName = entry.getName();
                if (fileName == null) {
                    LOG.warn("Null Entry Name");
                    ok = false;
                    continue;
                }
                if (FileUtils.getFileExtension(fileName).equals("patch")) {
                    String newFileName = fileName.substring(0, fileName.length() - ".patch".length());
                    if (newFileName.endsWith("."))
                        newFileName = newFileName.substring(0, newFileName.length() - 1);
                    Path toPatch = gameDir.resolve(newFileName);
                    if (!Files.exists(toPatch)) {
                        LOG.warn("File is missing {}", toPatch);
                        ok = false;
                        continue;
                    }
                    Path sourcePath = Paths.get(toPatch.toString() + ".old" + timestamp);
                    Files.move(toPatch, sourcePath);
                    byte[] bytes = Files.readAllBytes(sourcePath);
                    ByteBufferOutputStream readPatchStream = new ByteBufferOutputStream();
                    readEntry(zipStream, readPatchStream);

                    try {
                        Patch.patch(bytes, readPatchStream.getBuf(), Files.newOutputStream(toPatch));
                    } catch (InvalidHeaderException | CompressorException e) {
                        ok = false;
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
            LOG.info("Patching complete...");
            for (String toDeletePath : packageManifest.delete) {
                Path toDelete = gameDir.resolve(toDeletePath);
                LOG.info("Deleting {}", toDeletePath);
                // Save the .old files in case the update is interrupted
                Files.move(toDelete, Paths.get(toDelete + ".old" + timestamp));
            }

            for (String hashAndFile : packageManifest.hashes) {
                int colon = hashAndFile.indexOf(':');
                String hash = hashAndFile.substring(0, colon);
                String file = hashAndFile.substring(colon+1);
                MessageDigest hasher = Hashing.getMessageDigest();
                if (hasher == null) return false;
                try (DigestInputStream digest = new DigestInputStream(Files.newInputStream(gameDir.resolve(file)), hasher)) {
                    while (digest.read(readEntryBuffer) != -1) {}
                }
                String fileHash = Hashing.printHexBinary(hasher.digest());
                if (!hash.toLowerCase().equals(fileHash.toLowerCase())) {
                    LOG.info("Hashes for file {} do not match. Manifest - {}, File - {}", gameDir.resolve(file), hash, fileHash);
                    ok = false;
                }
            }

            currentBuild = new Build(gameDir);
        }  catch (IOException e) {
            LOG.info("IO EXception: ", e);
            updateVisibleCallback.accept(false);
            throw e;
        }

        BuildManifest manifest = new BuildManifest();
        manifest.version = new Integer[]{ packageManifest.target.major, packageManifest.target.minor, packageManifest.target.patch };
        manifest.dev = packageManifest.target.dev_build;
        manifest.exec = packageManifest.newExec;

        updateProgressCallback.accept(0.0);
        // These next two lines need to be atomic, but toFile.delete() can't fail, so all OK
        Files.write(gameDir.resolve("manifest.json"), gson.toJson(manifest).getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
        updateLog.toFile().delete();
        currentBuild = new Build(gameDir);

        updateStatusCallback.accept("Cleaning up...");
        clearOldFiles(gameDir.toFile());
        updateStatusCallback.accept("Update complete. " + packageManifest.target + " ready to play!");
        updateProgressCallback.accept(1.0);

        return ok;
    }

    private static byte[] readEntryBuffer = new byte[1024];
    private void readEntry(ZipInputStream zip, OutputStream out) throws IOException {
        int len;
        while ((len = zip.read(readEntryBuffer)) > 0) {
            out.write(readEntryBuffer, 0, len);
        }
    }

    private void confirmFileSize(long size) {
        long mb = Math.max(1, size / 1000000);
        var acc = FX.accept("Operation Confirmation", "This operation may require up to " + mb + "MB of disk sapace");
        if (!(acc.orElse(false))) {
            throw new CancellationException();
        }
    }

    public Build freshBuild(boolean devBuild) throws IOException, HashMismatchException {

        LOG.info("Making fresh build");
        if (!Files.exists(gameDir))
            Files.createDirectory(gameDir);
        else {
            HashSet<File> exclude = new HashSet<>();
            exclude.add(gameDir.resolve("launcher.log").toFile());
            FileUtils.deleteRecursive(gameDir.toFile(), exclude);
        }
        updateVisibleCallback.accept(true);
        updateStatusCallback.accept("Downloading Build /launcher/build");
        InputStream game = null;
        try {
            GameVersion gv = wrapper.getCurrentVersion(devBuild);
            if (gv ==null)
                throw new IOException("Failed to get current version");

            long size = wrapper.getFileSize(backendEndpoint + "/game/build/" + gv.toString());
            game = wrapper.getFile(backendEndpoint + "/game/build/" + gv.toString(), updateProgressCallback);
            updateStatusCallback.accept("Unpacking build");
            FileUtils.extractInputstream(game, gameDir, updateProgressCallback);
        } catch (IOException | HashMismatchException | NullPointerException e) {
            updateVisibleCallback.accept(false);
            throw e;
        }

        updateVisibleCallback.accept(false);
        currentBuild = new Build(gameDir);
        return currentBuild;
    }

    private static DecimalFormat df = new DecimalFormat("#.##");

    public static String formatDataSize(long bytes) {
        String[] extensions = {"B", "KB", "MB", "GB", "TB"};
        int extensionIndex = 0;
        double size = bytes;
        while (Math.abs(size / 1024) >= 1) {
            size /= 1024;
            extensionIndex++;
        }
        return df.format(size)  + extensions[extensionIndex];
    }
}
