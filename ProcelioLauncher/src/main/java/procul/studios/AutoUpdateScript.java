package procul.studios;

import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.Patch;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import org.apache.commons.compress.compressors.CompressorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.delta.Build;
import procul.studios.delta.BuildManifest;
import procul.studios.delta.DeltaManifest;
import procul.studios.util.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static procul.studios.gson.GsonSerialize.gson;

public class AutoUpdateScript extends RowEditor {
    private static final String downloadFolder = "_download";
    private static final Logger LOG = LoggerFactory.getLogger(AutoUpdateScript.class);

    private EndpointWrapper wrapper;
    Consumer<Boolean> visible;
    Consumer<String> msg;
    Runnable closeWindow;
    Supplier<String> installPath;

    public AutoUpdateScript(EndpointWrapper wrapper, Consumer<Boolean> visibleCallback, Consumer<String> messageCallback, Runnable closeWindow) {
        this.wrapper = wrapper;
        this.closeWindow = closeWindow;
        this.visible = visibleCallback;
        this.msg = messageCallback;
        installPath = addDirectoryRow("Launcher Directory", new File(".").getAbsolutePath());

        HBox buttons = new HBox();
        buttons.setAlignment(Pos.BASELINE_RIGHT);
        buttons.setSpacing(15);
        buttons.setPadding(new Insets(10));
        this.setBottom(buttons);

        Button accept = new Button("Accept");
        accept.setDefaultButton(true);
        accept.setPadding(new Insets(5, 15, 5, 15));
        accept.addEventHandler(ActionEvent.ACTION, event -> execute());
        buttons.getChildren().add(accept);

        Button cancel = new Button("Cancel");
        cancel.setCancelButton(true);
        cancel.setPadding(new Insets(5, 15, 5, 15));
        cancel.addEventHandler(ActionEvent.ACTION, event -> closeWindow.run());
        buttons.getChildren().add(cancel);
    }

    private void execute() {
        try {
            InputStream s = new FileInputStream("C:\\Users\\Brennan\\source\\repos\\C++Testbunker\\Release\\TestLaunch\\download.zip");
            execute(s);
        } catch (IOException e) {
            LOG.error(e.getMessage());
        }
    }

    private void execute(InputStream zip) throws IOException {
        closeWindow.run();
        visible.accept(true);
        msg.accept("downloading new launcher...");
        Path fold = Path.of(this.installPath.get()).resolve(downloadFolder);
        if (fold.toFile().exists()) {
            LauncherUtilities.deleteRecursive(fold.toFile());
        }


        try (ZipInputStream zipStream = new ZipInputStream(zip)) {
            ZipEntry entry = zipStream.getNextEntry();
            while (entry != null) {
                String entryName = entry.getName();
                entryName = entryName.replace("\\", Matcher.quoteReplacement(File.pathSeparator));
                Path p = Path.of(entry.getName());

                entry = zipStream.getNextEntry();

                Path newpath = fold.
            }



                 /*    while ((entry = zipStream.getNextEntry()) != null) {
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
                       Path sourcePath = Paths.get(toPatch.toString() + ".old" + timestamp);
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

               for (String toDeletePath : packageManifest.delete) {
                   Path toDelete = gameDir.resolve(toDeletePath);
                   LOG.info("Deleting {}", toDeletePath);
                   // Save the .old files in case the update is interrupted
                   Files.move(toDelete, Paths.get(toDelete + ".old" + timestamp));
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

               currentBuild = new Build(gameDir);*/
        } catch (IOException e) {
            msg.accept("Launcher update failed");
            throw e;
        }
        visible.accept(false);
    }

}
