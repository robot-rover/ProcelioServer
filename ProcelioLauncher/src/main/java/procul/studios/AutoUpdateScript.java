package procul.studios;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.nio.file.*;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static procul.studios.ProcelioLauncher.backendEndpoint;

public class AutoUpdateScript extends RowEditor {
    private static final Logger LOG = LoggerFactory.getLogger(AutoUpdateScript.class);

    AutoUpdate au;
    Application ap;

    public AutoUpdateScript(Application app, EndpointWrapper wrapper, Consumer<Boolean> visibleCallback, Consumer<String> messageCallback, Runnable closeWindow) {
        ap = app;
        LOG.info("" + wrapper.osHeaderValue);
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
        ts = new Task<>() {
            @Override
            protected String call() throws Exception {
                return au.ready();
            }
        };
        alertUpdate = new Task() {

            @Override
            protected Object call() throws Exception {
                while (true) {
                    if (isCancelled())
                        return null;
                    Thread.sleep(500);

                }
            }
        };
        updatingStatus = new Alert(Alert.AlertType.INFORMATION);
        updatingStatus.setTitle("Update launcher");
        updatingStatus.setHeaderText("Downloading ");
        ts.setOnSucceeded(this::done);
        new Thread(ts).start();
        Timeline dotDitter = new Timeline(new KeyFrame(Duration.seconds(.5), new EventHandler<ActionEvent>()
        {
            int numDots = 0;

            @Override
            public void handle(ActionEvent event)
            {
                char spinner = '|';
                switch (numDots % 4) {
                    case 1:
                        spinner = '/';
                        break;
                    case 2:
                        spinner = '-';
                        break;
                    case 3:
                        spinner = '\\';
                        break;
                }
                ++numDots;
                if (numDots > 8)
                    numDots %= 8;
                updatingStatus.setHeaderText("Downloading" + ".".repeat(numDots)+ " ".repeat(8-numDots) + "        " + (numDots > 3 ? " " : "") + spinner);
            }
        }));

        updatingStatus.getButtonTypes().clear();
        dotDitter.setCycleCount(Timeline.INDEFINITE);
        dotDitter.play();
        updatingStatus.showAndWait();
    }

    private void done(WorkerStateEvent workerStateEvent) {
        alertUpdate.cancel();
        Platform.runLater(() -> {
            updatingStatus.close();
            String name = null;
            try {
                name = ts.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Update launcher");
            alert.setHeaderText("Close launcher and run " + name);
            Path updateDest = Path.of(LauncherUtilities.fixSeparators(au.installPath.get(), au.wrapper)).resolve(name);
            javafx.scene.control.Label label = new Label("file located at\n" + updateDest);
            label.setWrapText(true);
            alert.getDialogPane().setContent(label);
            ButtonType buttonTypeOne = new ButtonType("OK");
            alert.getButtonTypes().setAll(buttonTypeOne);
            Optional<ButtonType> result = alert.showAndWait();
            try {
                Desktop.getDesktop().open(updateDest.getParent().toFile());
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                ap.stop();
            } catch (Exception e) {
            }
            System.exit(0);
        });
    }

    Task<String> ts;
    Task alertUpdate;

    private Alert updatingStatus;
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
            Path path = Path.of(LauncherUtilities.fixSeparators(this.installPath.get(), wrapper)).resolve("_231232_TestFile_jldfnf");
            Files.createFile(path);
            Files.delete(path);
            return super.ready();
        } catch (IOException e) {
            // Couldn't create... Guess we need admin
        }

        try {
            String pp = Path.of(LauncherUtilities.fixSeparators(this.installPath.get(), wrapper)).resolve("RunLaunchUpdate.exe").toString();
            LOG.info(pp);
            String[] args = new String[]{"cmd.exe", "/C", pp};
            Process process = new ProcessBuilder(args)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();

            LOG.info("Process is running!");
            int tick = 0;
            process.waitFor();
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

class AutoUpdate {
    public static final String downloadFolder = "_download";
    protected static final Logger LOG = LoggerFactory.getLogger(AutoUpdateScript.class);

    public static String DoUpdate(String installPath, InputStream zip, EndpointWrapper wrapper, Consumer<String> msg) throws IOException {
        Path instPat = null;
        Path fold = null;
        if (installPath != null) {
            fold = Path.of(LauncherUtilities.fixSeparators(installPath, wrapper)).resolve(downloadFolder);
            instPat = Path.of(LauncherUtilities.fixSeparators(installPath, wrapper));
        } else {
            fold = Path.of(LauncherUtilities.fixSeparators(ProcelioLauncher.downloadPath, wrapper));
            instPat = fold.getParent();
            LOG.info("E -> " + instPat.toString());
        }
        LOG.info("FOLD: " + fold);
        LOG.info("INST: " + instPat);

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
            InputStream s = wrapper.getFile(backendEndpoint + "/launcher/build");
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
        return DoUpdate(installPath.get(), zip, this.wrapper, this.msg);
    }

    public String ready() {
        return execute();
    }

}
