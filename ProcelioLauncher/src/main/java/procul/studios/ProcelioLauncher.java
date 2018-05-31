package procul.studios;

import com.google.gson.Gson;
import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.Patch;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import org.apache.commons.compress.compressors.CompressorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.pojo.BuildManifest;
import procul.studios.pojo.PackageManifest;
import procul.studios.pojo.Server;
import procul.studios.pojo.response.LauncherConfiguration;
import procul.studios.pojo.response.LauncherDownload;
import procul.studios.util.*;

import javax.xml.bind.DatatypeConverter;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Main Class for the Procelio Game Launcher
 */
public class ProcelioLauncher extends Application {
    private static final Logger LOG = LoggerFactory.getLogger(ProcelioLauncher.class);

    public final Gson gson = null;

    /**
     * Constant determines the version of the launcher build
     */
    private static final Version launcherVersion = new Version(0, 0, 2);
    /**
     * Constant determines the endpoint for the Procelio Backend
     */
    static final String backendEndpoint = /*"https://api.sovietbot.xyz"*/ "http://127.0.0.1";
    // Used for callbacks updating progress of a download or patch
    private Tuple<Label, ProgressBar> download;
    // Used for callback to change visible tab
    private TabPane selectView;
    private Tab downloadsTab;
    private Tab updatesTab;

    /**
     * Directory to install the game
     */
    private File gameDir = new File("ProcelioGame");
    // Set by the launcher version check
    private boolean launcherOutOfDate = false;
    // Background Thread that runs download and diffing
    private Service<Void> updateService = new UpdateService();
    // Used for callback for window operations
    private Stage primaryStage;

    private EndpointWrapper wrapper;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {

        this.primaryStage = primaryStage;
        primaryStage.setMinHeight(380);
        primaryStage.setMinWidth(550);
        wrapper = new EndpointWrapper();
        primaryStage.setTitle("Procelio Launcher v" + launcherVersion);

        primaryStage.getIcons().add(new Image(ClassLoader.getSystemResourceAsStream("icon.png")));
        BorderPane root = new BorderPane();

        HBox titleBox = new HBox();
        titleBox.setPadding(new Insets(10));
        titleBox.setSpacing(100);
        StackPane logoCollider = new StackPane();
        ImageView logo = new ImageView(new Image(ClassLoader.getSystemResourceAsStream("banner.png")));
        logo.setSmooth(true);
        logo.preserveRatioProperty().setValue(true);
        logo.setFitHeight(100);
        logo.setBlendMode(BlendMode.values()[0]);
        logoCollider.setOnMouseClicked(event -> {
            try {
                openBrowser(wrapper.getConfig().websiteUrl);
            } catch (IOException e) {
                LOG.warn("Unable to connect to server");
                FX.dialog("Connection Error", "Unable to communicate with Procelio Servers", Alert.AlertType.WARNING);
            }
        });
        logoCollider.setOnMouseEntered(event -> logo.setBlendMode(BlendMode.DIFFERENCE));
        logoCollider.setOnMouseExited(event -> logo.setBlendMode(null));
        logoCollider.getChildren().add(logo);
        titleBox.getChildren().add(logoCollider);
        VBox motd = new VBox();
        motd.setPadding(new Insets(10));
        motd.setAlignment(Pos.CENTER_RIGHT);
        Label motdText = null;
        try {
            motdText = new Label(wrapper.getConfig().quoteOfTheDay);
        } catch (IOException e) {
            motdText = new Label("No connection to server");
        }
        motdText.setWrapText(true);
        motd.getChildren().add(motdText);
        Label motdAuthor = null;
        try {
            motdAuthor = new Label("- " + wrapper.getConfig().quoteAuthor);
        } catch (IOException e) {
            motdAuthor = new Label();
        }
        motdAuthor.setAlignment(Pos.TOP_RIGHT);
        motd.getChildren().add(motdAuthor);
        titleBox.getChildren().add(motd);
        HBox.setHgrow(motd, Priority.ALWAYS);
        root.topProperty().setValue(titleBox);

        HBox launchBox = new HBox();
        launchBox.setPadding(new Insets(10));
        launchBox.setAlignment(Pos.CENTER_RIGHT);
        Button launch = new Button("Launch");
        launch.setOnAction(this::launchClick);
        launchBox.getChildren().add(launch);
        root.bottomProperty().setValue(launchBox);

        selectView = new TabPane();
        selectView.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        updatesTab = new Tab();
        updatesTab.setText("Updates");
        ScrollPane updateScroller = new ScrollPane();
        updateScroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        updateScroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        updateScroller.fitToWidthProperty().setValue(true);
        VBox updateArea = new VBox();
        updateArea.setPadding(new Insets(10));
        updateArea.setSpacing(10);
        List<LauncherConfiguration.Update> updates = null;
        try {
            updates = wrapper.getConfig().updates;
        } catch (IOException e) {
            LOG.warn("Unable to connect to server");
        }
        StackPane lineHolder = new StackPane(new Line(0, 0, 50, 0));
        lineHolder.setAlignment(Pos.CENTER);
        updateArea.getChildren().add(lineHolder);
        if (updates != null)
            for (LauncherConfiguration.Update update : updates) {
                HBox entry = new HBox();
                entry.setPadding(new Insets(10));
                entry.setSpacing(10);
                if (update.image != null) {
                    try {
                        ImageView updateIcon = new ImageView(wrapper.getImage(update.image));
                        updateIcon.setSmooth(true);
                        updateIcon.preserveRatioProperty().setValue(true);
                        updateIcon.setFitHeight(100);
                        entry.getChildren().add(updateIcon);
                    } catch (IOException e) {
                        LOG.warn("Unable to fetch image {}", update.image, e);
                    }
                }
                TextFlow description = new TextFlow();
                if (update.version != null && update.version.length == 3) {
                    Text version = new Text("Version " + update.version[0] + "." + update.version[1] + "." + update.version[2] + "\n");
                    version.setFont(Font.font(20));
                    description.getChildren().add(version);
                }
                description.getChildren().add(new Text(update.description));

                if (update.hyperlink != null) {
                    Text link = new Text("\n" + update.hyperlink);
                    link.setOnMouseClicked((MouseEvent event) -> {
                        openBrowser(update.hyperlink);
                    });
                    link.setUnderline(true);
                    description.getChildren().add(link);
                }
                entry.getChildren().add(description);
                updateArea.getChildren().add(entry);


                StackPane lineHolderCopy = new StackPane(new Line(0, 0, 50, 0));
                lineHolder.setAlignment(Pos.CENTER);
                updateArea.getChildren().add(lineHolderCopy);
            }
        updateScroller.contentProperty().setValue(updateArea);
        updatesTab.setContent(updateScroller);
        selectView.getTabs().add(updatesTab);

        downloadsTab = new Tab();
        downloadsTab.setText("Download");
        VBox downloadContent = new VBox();
        downloadContent.setAlignment(Pos.CENTER);
        downloadContent.setSpacing(10);
        downloadContent.setPadding(new Insets(10));
        ProgressBar downloadProgress = new ProgressBar();

        downloadProgress.setMinSize(500, 20);
        downloadProgress.setProgress(0);
        downloadContent.getChildren().add(downloadProgress);
        Label downloadStatus = new Label("No download in progress");
        downloadContent.getChildren().add(downloadStatus);
        downloadsTab.setContent(downloadContent);
        download = new Tuple<>(downloadStatus, downloadProgress);
        selectView.getTabs().add(downloadsTab);
        root.centerProperty().setValue(selectView);

        try {
            Version newestLauncher = new Version(wrapper.getConfig().launcherVersion);
            launcherOutOfDate = newestLauncher.compareTo(launcherVersion) > 0;
        } catch (IOException e) {
            LOG.warn("Cannot connect to server");
        }

        primaryStage.setScene(new Scene(root, 800, 400));

        primaryStage.show();

    }

    /**
     * Opens a link in the user's browser
     * @param url the URL to open
     */
    public void openBrowser(String url) {
        try {
            LOG.info("Opening " + url);
            Desktop desktop = Desktop.getDesktop();
            if(desktop == null) {
                throw new Exception("Java Desktop class is not supported");
            }
            URI oURL = new URI(url);
            desktop.browse(oURL);
        } catch (Exception e) {
            LOG.error("Error opening hyperlink " + url, e);
        }
    }

    //Listener for click on Launch Button
    private void launchClick(ActionEvent e) {
        if (updateService.getState().equals(Worker.State.RUNNING) || updateService.getState().equals(Worker.State.SCHEDULED)) {
            LOG.warn("Unable to start updateService. Current State: {}", updateService.getState());
        } else {
            updateService.restart();
        }
    }

    /**
     * Gets the game ready to run and then launches it
     */
    public void launchGame() {
        if (launcherOutOfDate) { // Don't allow the user to use a launcher that's out of date
            FX.dialog("Launcher Out of Date", "You need to download a new version of the launcher!", Alert.AlertType.INFORMATION);
            return;
        }
        BuildManifest manifest = null;
        try {
            // if a manifest exists, load it
            if (gameDir.exists() && new File(gameDir, "manifest.json").exists()) {
                manifest = EndpointWrapper.gson.fromJson(new FileReader(new File(gameDir, "manifest.json")), BuildManifest.class);
            }

            // if a manifest doesn't exist or isn't valid, download a fresh install of the game and then launch
            if (!gameDir.exists() || !new File(gameDir, "manifest.json").exists() || manifest == null || manifest.exec == null || manifest.version == null) {
                freshBuild();
                manifest = EndpointWrapper.gson.fromJson(new InputStreamReader(new FileInputStream(new File(gameDir, "manifest.json"))), BuildManifest.class);
                launchFile(new File(gameDir, manifest.exec));
                return;
            }
        } catch (IOException e) {
            FX.dialog("Connection Error", "Unable to download game\n" + e.getMessage(), Alert.AlertType.ERROR);
            LOG.warn("Error downloading and starting game", e);
            return;
        } catch (HashMismatchException e) {
            FX.dialog("Hash Mismatch", "Downloaded build but the file was fragmented", Alert.AlertType.ERROR);
            LOG.warn("Hash Mismatch", e);
            return;
        }

        // Didn't need a fresh build of the game, so we can attempt to patch
        try {
            try {
                updateBuild(manifest);
            } catch (IOException e) {
                LOG.warn("Unable to connect to server");
            } catch (HashMismatchException e) {
                FX.dialog("Hash Mismatch", "Downloaded build but the file was corrupted", Alert.AlertType.ERROR);
                LOG.warn("Hash Missmatch", e);
            }

            // even if patch failed, still run the game
            launchFile(new File(gameDir, manifest.exec));
        } catch (IOException e) {
            FX.dialog("Unable to Launch", "The launcher encountered an error and is unable to launch Procelio\n" + e.getMessage(), Alert.AlertType.ERROR);
            LOG.warn("Unable to launch game", e);
        }
    }

    /**
     * Attempt to patch the game
     * @param manifest the build manifest of the current version
     * @throws IOException if unable to contact backend server
     * @throws HashMismatchException if a file was downloaded twice and was corrupted both times
     */
    public void updateBuild(BuildManifest manifest) throws IOException, HashMismatchException {
        LauncherDownload gameStatus = wrapper.checkForUpdates(new Version(manifest.version));

        // if the versions match, the game is up to date
        if (gameStatus.upToDate) {
            LOG.info("All up to date");
            return;
        }

        // if no patch path is available, only option is a fresh build
        if (gameStatus.patches == null) {
            freshBuild();
            return;
        }

        // All set to patch
        LOG.info("Patching Build");
        for (String patch : gameStatus.patches) {
            updateProgressStatus("Downloading Patch " + patch);
            selectView.getSelectionModel().select(downloadsTab);
            InputStream input = wrapper.getFile(backendEndpoint + patch, this::updateProgressBar);
            applyPatch(input, manifest);
        }
    }

    public void savePatch(InputStream patch, BuildManifest manifest) throws IOException {
        File outputFile = new File("patch");
        outputFile.mkdir();
        FileUtils.extractInputstream(patch, outputFile);
    }

    private void updateProgressBar(double percent) {
        Platform.runLater(() -> {
            download.getSecond().setProgress(percent);
        });
    }

    private void updateProgressStatus(String status) {
        Platform.runLater(() -> {
            download.getFirst().setText(status);
            LOG.info("Download Status: {}", status);
        });
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
            updateProgressStatus("Patching " + new Version(packageManifest.fromVersion) + " -> " + new Version(packageManifest.toVersion));
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
                    try (ByteArrayOutputStream patchStream = new ByteArrayOutputStream()) {
                        readEntry(zipStream, buffer, patchStream);
                        OutputStream patchedOut = new FileOutputStream(toPatch, false);
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
                DigestInputStream digest = new DigestInputStream(new FileInputStream(new File(gameDir, file)), hasher);
                while (digest.read(buffer) != -1) {
                }
                String fileHash = DatatypeConverter.printHexBinary(hasher.digest());
                if (!hash.equals(fileHash)) {
                    LOG.info("Hashes for file {} do not match. Manifest - {}, File - {}", new File(gameDir, file), hash, fileHash);
                }
            }
        }
    }

    private void readEntry(ZipInputStream zip, byte[] buffer, OutputStream out) throws IOException {
        int len;
        while ((len = zip.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
    }

    public void freshBuild() throws IOException, HashMismatchException {
        LOG.info("Making fresh build");
        if (!gameDir.exists())
            gameDir.mkdir();
        else
            FileUtils.deleteRecursive(gameDir);
        updateProgressStatus("Downloading Build /launcher/build");
        selectView.getSelectionModel().select(downloadsTab);
        InputStream hashes = wrapper.getFile(backendEndpoint + "/launcher/build", this::updateProgressBar);
        FileUtils.extractInputstream(hashes, gameDir);
    }

    public void launchFile(File executable) throws IOException {
        updateProgressStatus("Launching ProcelioGame");
        LOG.info("Launching " + executable.getAbsolutePath());
        executable.setExecutable(true, false);
        executable.setReadable(true);
        Server[] servers;
        String hostname = null;
        LOG.info(Arrays.toString(executable.getParentFile().list()));
        try {
            servers = wrapper.getServers();
            hostname = servers[0].hostname;
        } catch (IOException e) {
            hostname = "";
            while (hostname.length() == 0) {
                hostname = FX.prompt("Unable to aquire Server", "Unable to automatically connect to game server,\n please specify a server address").orElse(null);
                if (hostname == null)
                    return;
            }
        }
        LOG.info("Running {} in directory {} - Exists: {}", gameDir.toPath().relativize(executable.toPath()), gameDir, executable.exists());
        Process game = new ProcessBuilder(executable.getAbsolutePath(), hostname, "-client").directory(gameDir).inheritIO().start();
        Platform.runLater(() -> primaryStage.setIconified(true));
        try {
            game.waitFor();
        } catch (InterruptedException e) {
            LOG.warn("Unable to un-iconify", e);
        }
        LOG.info(executable.getAbsolutePath() + " Exited");
        Platform.runLater(() -> {
            primaryStage.setIconified(false);
            primaryStage.toFront();
        });
    }

    class UpdateService extends Service<Void> {
        @Override
        protected Task<Void> createTask() {
            return new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    launchGame();
                    return null;
                }
            };
        }
    }
}
