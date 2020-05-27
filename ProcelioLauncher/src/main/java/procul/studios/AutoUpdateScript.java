package procul.studios;

import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.Patch;
import javafx.application.Application;
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
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static procul.studios.ProcelioLauncher.backendEndpoint;
import static procul.studios.gson.GsonSerialize.gson;

public class AutoUpdateScript extends RowEditor {
    private static final Logger LOG = LoggerFactory.getLogger(AutoUpdateScript.class);

    AutoUpdate au;
    Application ap;
    public AutoUpdateScript(Application app, EndpointWrapper wrapper, Consumer<Boolean> visibleCallback, Consumer<String> messageCallback, Runnable closeWindow) {
        ap = app;
        switch (wrapper.osHeaderValue) {
            case WINDOWS:
                // Go through elevation layer. Ugh.
                au = new AutoUpdateWindows(wrapper, visibleCallback, messageCallback, closeWindow);
                break;
            default:
                au = new AutoUpdate(wrapper, visibleCallback, messageCallback, closeWindow);
        }
        addTextRow("Launcher out of date!");
        addTextRow("Select the root folder of the launcher application:");
        au.installPath = addDirectoryRow("Launcher Directory", new File(".").getAbsolutePath());
        addTextRow("Windows: the default path should be correct if the application was run normally");
        addTextRow("Linux: the default path should be the folder containing bin, not bin itself");
        addTextRow("");
        addTextRow("DO NOT TURN OFF YOUR COMPUTER WHILE UPDATE IS IN PROGRESS");
        HBox buttons = new HBox();
        buttons.setAlignment(Pos.BASELINE_RIGHT);
        buttons.setSpacing(15);
        buttons.setPadding(new Insets(10));
        this.setBottom(buttons);

        Button accept = new Button("Accept");
        accept.setDefaultButton(true);
        accept.setPadding(new Insets(5, 15, 5, 15));
        accept.addEventHandler(ActionEvent.ACTION, event -> this.ready());
        buttons.getChildren().add(accept);

        Button cancel = new Button("Cancel");
        cancel.setCancelButton(true);
        cancel.setPadding(new Insets(5, 15, 5, 15));
        cancel.addEventHandler(ActionEvent.ACTION, event -> closeWindow.run());
        buttons.getChildren().add(cancel);
    }

    private void ready() {
        String name = au.ready();
        LOG.info("updated " + name);
        Path updateDest = Path.of(LauncherUtilities.fixSeparators(au.installPath.get(), au.wrapper)).resolve(name);
        LOG.info("ne file: "+updateDest.toString());

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Update launcher");
        alert.setHeaderText("Close launcher and run " + name);

        javafx.scene.control.Label label = new Label("file located at\n"+updateDest);
        label.setWrapText(true);
        alert.getDialogPane().setContent(label);

        ButtonType buttonTypeOne = new ButtonType("OK");
        alert.getButtonTypes().setAll(buttonTypeOne);
        Optional<ButtonType> result = alert.showAndWait();
        try {
            Desktop.getDesktop().open(updateDest.getParent().toFile());
        } catch (IOException e) { e.printStackTrace(); }
        try {
            ap.stop();
        } catch (Exception e) {}
        System.exit(0);
    }
}

class AutoUpdateWindows extends AutoUpdate {

    public AutoUpdateWindows(EndpointWrapper wrapper, Consumer<Boolean> visibleCallback, Consumer<String> messageCallback, Runnable closeWindow) {
        super(wrapper, visibleCallback, messageCallback, closeWindow);
    }

    public AutoUpdateWindows(EndpointWrapper wrapper) {
        super(wrapper);
    }

    public String ready() {
        try {
            String pp = Path.of(LauncherUtilities.fixSeparators(this.installPath.get(), wrapper)).resolve("RunLaunchUpdate.exe").toString();
            LOG.info(pp);
            String[] args = new String[] {"cmd.exe", "/C", pp};
            Process process = new ProcessBuilder(args)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();

            LOG.info("Process is running!");
            int tick = 0;
            while (!process.waitFor(300, TimeUnit.MILLISECONDS)) {
                updateGrid(tick++);
            }
            LOG.info("concluded");
            Path p = Path.of(LauncherUtilities.fixSeparators(this.installPath.get(), wrapper)).resolve(downloadFolder);
            File[] arr = p.toFile().listFiles();
            if (arr == null)
                throw new IOException("wrong folder?");
            String name = "";
            for (File f : arr) {
                if (f.getName().toLowerCase(Locale.ENGLISH).contains("launcherupdate")) {
                    name = f.getName();
                    break;
                }
            }
            return name;

        } catch (IOException | InterruptedException e) {
            LOG.error(e.getMessage());
            e.printStackTrace();
        }
        return "";
    }
}

class AutoUpdate  {
   protected static final String downloadFolder = "_download";
   protected static final Logger LOG = LoggerFactory.getLogger(AutoUpdateScript.class);

    EndpointWrapper wrapper;
    Consumer<Boolean> visible;
    Consumer<String> msg;
    Runnable closeWindow;
    Supplier<String> installPath;

    public AutoUpdate(EndpointWrapper wrapper, Consumer<Boolean> visibleCallback, Consumer<String> messageCallback, Runnable closeWindow) {
        this.wrapper = wrapper;
        this.closeWindow = closeWindow;
        this.visible = visibleCallback;
        this.msg = messageCallback;

    }

    public AutoUpdate(EndpointWrapper wrapper) {
        this.wrapper = wrapper;
        this.visible = null;
        this.msg = null;
        this.closeWindow = null;
        this.installPath = null;
        execute();
    }

    public String execute() {
        try {
            if (visible != null) {
                visible.accept(true);
                msg.accept("downloading new launcher...");
            }
            InputStream s = wrapper.getFile(backendEndpoint + "/launcher/launcher");
            return execute(s);
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            LOG.error(sw.toString());
        }
        return "";
    }

    public String execute(InputStream zip) throws IOException {
        if (closeWindow != null)
            closeWindow.run();

        Path instPat = null;
        Path fold = null;
        if (installPath != null) {
            fold = Path.of(LauncherUtilities.fixSeparators(this.installPath.get(), wrapper)).resolve(downloadFolder);
            instPat = Path.of(LauncherUtilities.fixSeparators(this.installPath.get(), wrapper));
        }
        else {
            fold = Path.of(LauncherUtilities.fixSeparators(ProcelioLauncher.downloadPath, wrapper));
            instPat = fold.getParent();
            LOG.info("E -> " + instPat.toString());
        }

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
            if (msg != null)
                msg.accept("Launcher update failed");
            LOG.error("A: " + e.getMessage());
            throw e;
        }
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
        Path updateDest = instPat.resolve(name);
        Files.copy(update, updateDest, StandardCopyOption.REPLACE_EXISTING);
        return name;
    }

    public void updateGrid(int ticks) {
//        resetGrid();
        int numDots = ticks % 5;
        String s = ".".repeat(numDots);
  //      this.addTextRow("Starting update process" + s);
    //    this.addTextRow("(this may take a little while)");
    }

    public String ready() {
        return execute();
    }

}
