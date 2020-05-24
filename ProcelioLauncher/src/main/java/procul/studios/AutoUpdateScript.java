package procul.studios;

import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.Patch;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.apache.commons.compress.compressors.CompressorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.delta.Build;
import procul.studios.delta.BuildManifest;
import procul.studios.delta.DeltaManifest;
import procul.studios.util.*;

import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
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
                if (entry.isDirectory()) {
                    entry = zipStream.getNextEntry();
                    continue;
                }

                String entryName = entry.getName();
                Path p = Path.of(entryName);
                Path savePos = fold.resolve(p);
                Files.createDirectories(savePos.getParent());
                File f = savePos.toFile();
                FileOutputStream fos = new FileOutputStream(f, false);
                byte[] data = new byte[1024];
                int len = 0;
                while ((len = zipStream.read(data)) > 0) {
                    fos.write(data, 0, len);
                }
                fos.close();
                entry = zipStream.getNextEntry();
            }
        } catch (IOException e) {
            msg.accept("Launcher update failed");
            LOG.error(e.getMessage());
            throw e;
        }
        visible.accept(false);
        File[] arr = fold.toFile().listFiles();
        if (arr == null)
            throw new IOException("wrong folder?");
        String name = "";
        for (File f : arr) {
            if (f.getName().toLowerCase(Locale.ENGLISH).contains("launcherupdate")) {
                name = f.getName();
                break;
            }
        }
        if (name.equals(""))
            throw new IllegalStateException("Updater not found");

        Path update = fold.resolve(name);
        Path updateDest = Path.of(this.installPath.get()).resolve(name);
        Files.copy(update, updateDest, StandardCopyOption.REPLACE_EXISTING);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Update launcher");
        alert.setHeaderText("Close launcher and run " + name);

        javafx.scene.control.Label label = new Label("file located at\n"+updateDest);
        label.setWrapText(true);
        alert.getDialogPane().setContent(label);
        ButtonType buttonTypeOne = new ButtonType("OK");
        alert.getButtonTypes().setAll(buttonTypeOne);
        Optional<ButtonType> result = alert.showAndWait();
        Desktop.getDesktop().open(updateDest.getParent().toFile());
        System.exit(0);
    }

}
