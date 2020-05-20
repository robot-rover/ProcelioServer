package procul.studios;

import com.google.gson.JsonParseException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;
import net.harawata.appdirs.AppDirs;
import net.harawata.appdirs.AppDirsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.delta.Build;
import procul.studios.gson.LauncherConfiguration;
import procul.studios.util.HashMismatchException;
import procul.studios.util.Tuple;
import procul.studios.util.Version;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static procul.studios.gson.GsonSerialize.gson;

/**
 * Main Class for the Procelio Game Launcher
 */
public class ProcelioLauncher extends Application {
    private static final Logger LOG = LoggerFactory.getLogger(ProcelioLauncher.class);

    public static final AppDirs appDirs = AppDirsFactory.getInstance();

    public static final String procelioOrange = "#FF7500";

    /**
     * Constant determines the version of the launcher build
     */
    private static final Version launcherVersion = new Version(0, 0, 4);

    /**
     * Constant determines the endpoint for the Procelio Backend
     */
    static final String backendEndpoint;
    private static final boolean debugEndpoint = false;
    static {
        if(debugEndpoint) {
            backendEndpoint = "http://127.0.0.1";
        } else {
            backendEndpoint = "https://www.sovietbot.xyz:8443";
        }
    }
    // Used for callbacks updating progress of a download or patch
    private Tuple<Label, ProgressBar> downloadCallbackTuple;

    /**
     * Directory to install the game
     */
    static Path gameDir;
    static final String defaultGameDir = System.getProperty("user.home") + File.separator + ".ProcelioGame";

    private static Path readmeFile;

    private static Path settingsFile;

    private LauncherSettings settings;

    // Set by the launcher version check
    private boolean launcherOutOfDate = false;

    // Background Thread that runs download and diffing
    private Service<Void> updateService = new UpdateService();

    // Used for callback for window operations
    private Stage primaryStage;

    private EndpointWrapper wrapper;

    private Patcher patcher;
    int width;
    int height;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() {
        settingsFile = Paths.get(appDirs.getUserConfigDir("procelioLauncher", "data", "proculStudios", false)).resolve("config.json");
        LOG.info("Settings File: {}", settingsFile);
        try {
            settings = gson.fromJson(Files.newBufferedReader(settingsFile), LauncherSettings.class);
        } catch (JsonParseException | IOException e) {
            LOG.warn("Unable to load Launcher Setting", e);
        }
        if(settings == null)
            settings = new LauncherSettings();
        if(settings.acceptedReadme == null)
            settings.acceptedReadme = false;
        if(settings.installDir == null)
            settings.installDir = defaultGameDir;
        gameDir = Paths.get(settings.installDir);

        readmeFile = gameDir.resolve("README.txt");


        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                settings.windowSize = new int[]{width, height};
                String json = gson.toJson(settings);
                Files.createDirectories(settingsFile.getParent());
                Files.write(settingsFile, json.getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
                LOG.info("Settings Saved");
            } catch (IOException e) {
                LOG.warn("Unable to Save Launcher Settings", e);
            }
        }));
        LOG.info("Game Directory: {}", gameDir);

        Font.loadFont(ClassLoader.getSystemResource("ShareTech-Regular.ttf").toExternalForm(), -1);
        LOG.info("Current Module: {}", this.getClass().getModule());

    }

    @Override
    public void start(Stage primaryStage) {

        wrapper = new EndpointWrapper();

        try {
            patcher = new Patcher(gameDir, wrapper, this::updateProgressVisible, this::updateProgressBar, this::updateProgressStatus);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Launcher Version Check
        try {
            Version newestLauncher = new Version(wrapper.getConfig().launcherVersion);
            launcherOutOfDate = newestLauncher.compareTo(launcherVersion) > 0;
            if (launcherOutOfDate) {
                LOG.warn("Launcher is out of date - Current: {}, Latest: {}", launcherVersion, newestLauncher);
            }
        } catch (IOException e) {
            LOG.warn("Cannot connect to server");
        }

        // Window
        this.primaryStage = primaryStage;
        width = 1136;
        height = 540;
        if(settings.windowSize != null && settings.windowSize.length == 2) {
            width = settings.windowSize[0];
            height = settings.windowSize[1];
        }
        // #setResizable(false) causes weird issues with the background
        primaryStage.setMinWidth(640);
        primaryStage.setWidth(width);
        primaryStage.setMaxWidth(1136);

        primaryStage.setMinHeight(480);
        primaryStage.setHeight(height);
        primaryStage.setMaxHeight(540);

        primaryStage.setTitle("A Procelio Launcher v" + launcherVersion);
        primaryStage.getIcons().add(ImageResources.load("icon.png"));
        primaryStage.heightProperty().addListener((observable, oldValue, newValue) -> height = newValue.intValue());
        primaryStage.widthProperty().addListener(((observable, oldValue, newValue) -> width = newValue.intValue()));
        BorderPane contentRoot = new BorderPane();

        // Title Bar
        HBox titleBox = new HBox();
        titleBox.setPadding(new Insets(10));
        titleBox.setSpacing(0);
        titleBox.setMaxHeight(120);
        contentRoot.topProperty().setValue(titleBox);

        // Logo
        StackPane logoCollider = new StackPane();
        logoCollider.setOnMouseClicked(event -> {
            try {
                openBrowser(wrapper.getConfig().websiteUrl);
            } catch (IOException e) {
                LOG.warn("Unable to connect to server");
                warnUserNoConnection();
            }
        });
        titleBox.getChildren().add(logoCollider);

        DropShadow dropShadow = new DropShadow();
        dropShadow.setRadius(10.0);
        dropShadow.setOffsetX(3.0);
        dropShadow.setOffsetY(3.0);

        ImageView logo = new ImageView(ImageResources.load("banner_scaled.png"));
        logo.setSmooth(true);
        logo.setEffect(dropShadow);
        logo.setPreserveRatio(true);
        logo.setFitHeight(100);
        logo.setCache(true);
        logo.setCacheHint(CacheHint.QUALITY);
        logo.setBlendMode(BlendMode.values()[0]);
        logoCollider.getChildren().add(logo);

        HBox socialBar = new HBox();
        socialBar.setPadding(new Insets(10));
        socialBar.setSpacing(10);
        titleBox.getChildren().add(socialBar);

        double logoWidth = 30;
        double logoHeight = 30;

        ImageView youtube = new ImageView(ImageResources.load("youtube_logo_small.png"));
        youtube.setPreserveRatio(true);
        youtube.setId("social");
        youtube.setOnMouseClicked(v -> openBrowser("https://www.youtube.com/channel/UCb9SlKVDpFMb3_BkcTNv8SQ"));
        youtube.setFitWidth(logoWidth);
        youtube.setFitHeight(logoHeight);
        socialBar.getChildren().add(youtube);

        ImageView twitter = new ImageView(ImageResources.load("twitter_logo_small.png"));
        twitter.setPreserveRatio(true);
        twitter.setId("social");
        twitter.setOnMouseClicked(v -> openBrowser("https://twitter.com/proceliogame?lang=en"));
        twitter.setFitWidth(logoWidth);
        twitter.setFitHeight(logoHeight);
        socialBar.getChildren().add(twitter);

        ImageView discord = new ImageView(ImageResources.load("discord_logo_small.png"));
        discord.setPreserveRatio(true);
        discord.setId("social");
        discord.setOnMouseClicked(v -> openBrowser("https://discord.gg/TDWKZzf"));
        discord.setFitWidth(logoWidth);
        discord.setFitHeight(logoHeight);
        socialBar.getChildren().add(discord);

        ImageView settings = new ImageView(ImageResources.load("gear_logo_small.png"));
        settings.setPreserveRatio(true);
        settings.setId("social");
        settings.setOnMouseClicked(this::openSettings);
        settings.setFitHeight(logoHeight);
        settings.setFitWidth(logoWidth);
        socialBar.getChildren().add(settings);

        VBox motd = new VBox();
        motd.setPadding(new Insets(10));
        motd.setAlignment(Pos.CENTER_RIGHT);
        titleBox.getChildren().add(motd);
        HBox.setHgrow(motd, Priority.ALWAYS);

        Label motdText;

        try {
            motdText = new Label(wrapper.getConfig().quoteOfTheDay);
        } catch (IOException e) {
            motdText = new Label("No connection to server");
        }
        motdText.setWrapText(true);
        motdText.setId("motd");
        motd.getChildren().add(motdText);

        Label motdAuthor;
        try {
            motdAuthor = new Label("- " + wrapper.getConfig().quoteAuthor);
        } catch (IOException e) {
            motdAuthor = new Label();
        }
        motdAuthor.setAlignment(Pos.TOP_RIGHT);
        motdAuthor.setId("motd");
        motdAuthor.setFont(Font.font("Share Tech"));
        motd.getChildren().add(motdAuthor);

        // News Area
        List<LauncherConfiguration.Update> updateList;
        try {
            updateList = wrapper.getConfig().updates;
        } catch (IOException | NullPointerException e) {
            LOG.warn("Cannot connect to server", e);
            updateList = new ArrayList<>();
        }
        NewsList newsList = new NewsList(updateList);
        contentRoot.setCenter(newsList);

        // Launch Bar
        HBox launchBar = new HBox();
        launchBar.setId("launchBar");
        launchBar.setPadding(new Insets(20, 40, 20, 40));
        launchBar.setFillHeight(true);
        launchBar.setAlignment(Pos.CENTER);
        launchBar.setSpacing(80);
        contentRoot.bottomProperty().setValue(launchBar);

        VBox downloadProgress = new VBox();
        downloadProgress.setVisible(false);
        downloadProgress.setFillWidth(true);
        downloadProgress.setAlignment(Pos.CENTER);
        downloadProgress.setSpacing(10);
        HBox.setHgrow(downloadProgress, Priority.ALWAYS);
        launchBar.getChildren().add(downloadProgress);

        ProgressBar downloadProgressBar = new ProgressBar();
        downloadProgressBar.setMinHeight(22);
        downloadProgressBar.setMaxWidth(Double.MAX_VALUE);
        downloadProgressBar.setProgress(0.0);
        downloadProgress.getChildren().add(downloadProgressBar);

        Label downloadProgressLabel = new Label("");
        downloadProgressLabel.setId("download");
        downloadProgressLabel.setTextFill(Color.WHITE);
        downloadProgress.getChildren().add(downloadProgressLabel);
        downloadCallbackTuple = new Tuple<>(downloadProgressLabel, downloadProgressBar);

        StackPane launchButtonHolder = new StackPane();
        launchButtonHolder.setAlignment(Pos.CENTER_RIGHT);
        launchButtonHolder.setPadding(new Insets(0, 0, 0, 0));
        launchBar.getChildren().add(launchButtonHolder);

        Button launchButton = new Button("Launch");
        launchButton.setOnAction(this::launchButtonClick);
        launchButton.setId("launch");
        launchButton.setPadding(new Insets(15, 45, 15, 45));
        launchButtonHolder.getChildren().add(launchButton);

        ImageView windowBackground = new ImageView(ImageResources.load("background.png"));
        windowBackground.setPreserveRatio(true);
        StackPane root = new StackPane(windowBackground, contentRoot);
        root.setAlignment(Pos.CENTER);

        Scene primaryScene = new Scene(root);
        primaryScene.getStylesheets().add("stylesheet.css");
        primaryStage.setScene(primaryScene);

        // Center Stage (Stage#centerOnScreen didn't work correctly)
        primaryStage.setX((Screen.getPrimary().getVisualBounds().getWidth() - width) / 2);
        primaryStage.setY((Screen.getPrimary().getVisualBounds().getHeight() - height) / 2);

        primaryStage.show();
    }

    private void openSettings(MouseEvent mouseEvent) {
        System.out.println("CLICK!");
        Stage settingsStage = new Stage();
        settingsStage.setMaxHeight(480);
        settingsStage.setHeight(400);
        settingsStage.setMaxWidth(640);
        settingsStage.setScene(new Scene(new ConfigEditor(settings, settingsStage::close)));
        settingsStage.show();
    }


    public static void debugNode(Node node) {
        node.setStyle("-fx-border-color: black");
    }

    private void warnUserNoConnection() {
        FX.dialog("Connection Error", "Unable to communicate with Procelio Servers", Alert.AlertType.WARNING);
    }

    /**
     * Opens a link in the user's browser
     * @param url the URL to open
     */
    public static void openBrowser(String url) {
        if(Platform.isFxApplicationThread()) {
            new Thread(() -> openBrowser(url)).start();
            return;
        }
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

    private void launchClick(MouseEvent me) {
        openSettings(null);
    }

    //Listener for click on Launch Button
    private void launchButtonClick(ActionEvent e) {
        if (!settings.configured) {
            openSettings(null);
            return;
        }
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
            FX.dialog("Launcher Out of Date", "You need to download a new version of the launcher!", Alert.AlertType.WARNING);
            return;
        }

        if (!settings.configured) { // Don't set up the game before first init
            return;
        }

        try {
            Files.createDirectories(gameDir);
        } catch (IOException e) {
            LOG.error("Cannot create game directory", e);
        }
        Build manifest = null;
        try {
            // if a manifest exists, load it
            if (Files.exists(gameDir) && Files.exists(gameDir.resolve("manifest.json"))) {
                manifest = patcher.currentBuild;
            }

            // if a manifest doesn't exist or isn't valid, download a fresh install of the game and then launch
            if (manifest == null || manifest.getManifest().exec == null || manifest.getManifest().version == null) {
                manifest = patcher.freshBuild();
                launchFile(gameDir.resolve(manifest.getManifest().exec));
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
            manifest = patcher.updateBuild();
        } catch (IOException e) {
            LOG.warn("Unable to connect to server");
            return;
        } catch (HashMismatchException e) {
            FX.dialog("Hash Mismatch", "Downloaded build but the file was corrupted.\n If error persists, reinstall game", Alert.AlertType.ERROR);
            LOG.warn("Hash Mismatch", e);
            return;
        }

        launchFile(gameDir.resolve(manifest.getManifest().exec));
    }



    private void updateProgressBar(double percent) {
        if(!downloadCallbackTuple.getFirst().getParent().isVisible()) {
            LOG.warn("update callback called but updateContent is not visible");
        }
        Platform.runLater(() -> downloadCallbackTuple.getSecond().setProgress(percent));
    }

    private void updateProgressStatus(String status) {
        if(!downloadCallbackTuple.getFirst().getParent().isVisible()) {
            LOG.warn("update callback called but updateContent is not visible");
        }
        Platform.runLater(() -> downloadCallbackTuple.getFirst().setText(status));
        LOG.info("Download Status: {}", status);
    }

    private void updateProgressVisible(boolean visible) {
        Platform.runLater(() -> downloadCallbackTuple.getFirst().getParent().setVisible(visible));
    }

    public void launchFile(Path executable) {
        if(!settings.acceptedReadme) {
            try {
                String readme = new String(Files.readAllBytes(readmeFile));
                if(!FX.acceptLong("Accept EULA", readme, "Do you accept this EULA?").orElse(false)) {
                    FX.dialog("EULA Declined", "You must accept the EULA to play the game", Alert.AlertType.WARNING);
                    return;
                } else {
                    settings.acceptedReadme = true;
                }
            } catch (IOException e) {
                LOG.warn("Cannot read readmeFile", e);
                return;
            }
        }

        boolean isReadable = executable.toFile().setReadable(true);
        boolean isExecutable = executable.toFile().setExecutable(true, false);
        LOG.info("Launching Procelio from {} - R:{}, X:{}", executable, isReadable, isExecutable);

        try {
            LOG.info(Files.newDirectoryStream(executable.getParent()).toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
        Process game;
        try {
            LOG.info("Running {} in directory {} - Exists: {}", gameDir.relativize(executable), gameDir, Files.exists(executable));
            List<String> concatArgs = new ArrayList<>();
            concatArgs.add(executable.normalize().toString());
            concatArgs.addAll(Arrays.asList(wrapper.getLaunchArgs()));
            LOG.info("Launching with args: {}", concatArgs);
            game = new ProcessBuilder(concatArgs).directory(gameDir.toFile()).redirectErrorStream(true).start();
            Platform.runLater(() -> primaryStage.setIconified(true));
        } catch (IOException e) {
            LOG.error("Unable to launch Procelio", e);
            FX.dialog("Unable to Launch", e.toString(), Alert.AlertType.ERROR);
            return;
        }
        try {
            game.waitFor();
            LOG.info("Procelio Output ->" + new String(game.getInputStream().readAllBytes()) + "\n");
        } catch (InterruptedException e) {
            LOG.warn("Un-Iconify started early", e);
        } catch (IOException e) {
            LOG.warn("IOException displaying game output");
        }
        LOG.info(executable + " Exited");
        Platform.runLater(() -> {
            primaryStage.setIconified(false);
            primaryStage.toFront();
        });
    }

    class UpdateService extends Service<Void> {
        @Override
        protected Task<Void> createTask() {
            return new Task<>() {
                @Override
                protected Void call() {
                    try {
                        launchGame();
                    } catch (Exception e) {
                        LOG.error("Error in UpdaterService", e);
                    }
                    return null;
                }
            };
        }
    }
}
