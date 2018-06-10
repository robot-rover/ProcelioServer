package procul.studios;

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
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.pojo.BuildManifest;
import procul.studios.pojo.Server;
import procul.studios.pojo.response.LauncherConfiguration;
import procul.studios.util.FX;
import procul.studios.util.HashMismatchException;
import procul.studios.util.Tuple;
import procul.studios.util.Version;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main Class for the Procelio Game Launcher
 */
public class ProcelioLauncher extends Application {
    private static final Logger LOG = LoggerFactory.getLogger(ProcelioLauncher.class);

    public static final String procelioOrange = "#FF7500";

    /**
     * Constant determines the version of the launcher build
     */
    private static final Version launcherVersion = new Version(0, 0, 0);

    /**
     * Constant determines the endpoint for the Procelio Backend
     */
    static final String backendEndpoint = /*"https://api.sovietbot.xyz"*/ "http://127.0.0.1";

    // Used for callbacks updating progress of a download or patch
    private Tuple<Label, ProgressBar> downloadCallbackTuple;

    /**
     * Directory to install the game
     */
    static final File gameDir = new File("ProcelioGame");

    // Set by the launcher version check
    private boolean launcherOutOfDate = false;

    // Background Thread that runs download and diffing
    private Service<Void> updateService = new UpdateService();

    // Used for callback for window operations
    private Stage primaryStage;

    private EndpointWrapper wrapper;

    private Patcher patcher;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() {
        Font.loadFont(ClassLoader.getSystemResource("ShareTech-Regular.ttf").toExternalForm(), -1);
        System.out.println(this.getClass().getModule());
    }

    @Override
    public void start(Stage primaryStage) {

        wrapper = new EndpointWrapper();

        patcher = new Patcher(wrapper, this::updateProgressVisible, this::updateProgressBar, this::updateProgressStatus);

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
        int width = 1136;
        int height = 540;
        // #setResizable(false) causes weird issues with the background
        primaryStage.setMinWidth(width);
        primaryStage.setMinHeight(height);
        primaryStage.setMaxWidth(width);
        primaryStage.setMaxHeight(height);

        primaryStage.setTitle("Procelio Launcher v" + launcherVersion);
        primaryStage.getIcons().add(new Image(ClassLoader.getSystemResourceAsStream("icon.png")));
        BorderPane contentRoot = new BorderPane();

        // Title Bar
        HBox titleBox = new HBox();
        titleBox.setPadding(new Insets(10));
        titleBox.setSpacing(0);
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

        ImageView logo = new ImageView(new Image(ClassLoader.getSystemResourceAsStream("banner_scaled.png")));
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

        ImageView youtube = new ImageView(new Image(ClassLoader.getSystemResourceAsStream("social/youtube_logo_small.png")));
        youtube.setPreserveRatio(true);
        youtube.setId("social");
        youtube.setOnMouseClicked(v -> openBrowser("https://www.youtube.com/channel/UCb9SlKVDpFMb3_BkcTNv8SQ"));
        youtube.setFitWidth(logoWidth);
        youtube.setFitHeight(logoHeight);
        socialBar.getChildren().add(youtube);

        ImageView twitter = new ImageView(new Image(ClassLoader.getSystemResourceAsStream("social/twitter_logo_small.png")));
        twitter.setPreserveRatio(true);
        twitter.setId("social");
        twitter.setOnMouseClicked(v -> openBrowser("https://twitter.com/proceliogame?lang=en"));
        twitter.setFitWidth(logoWidth);
        twitter.setFitHeight(logoHeight);
        socialBar.getChildren().add(twitter);

        ImageView discord = new ImageView(new Image(ClassLoader.getSystemResourceAsStream("social/discord_logo_small.png")));
        discord.setPreserveRatio(true);
        discord.setId("social");
        discord.setOnMouseClicked(v -> openBrowser("https://discord.gg/sKmA3QV"));
        discord.setFitWidth(logoWidth);
        discord.setFitHeight(logoHeight);
        socialBar.getChildren().add(discord);

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

        ImageView windowBackground = new ImageView(new Image(ClassLoader.getSystemResourceAsStream("background.png")));
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


    private void debugNode(Node node) {
        node.setStyle("-fx-border-color: black");
    }

    public void warnUserNoConnection() {
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

    //Listener for click on Launch Button
    private void launchButtonClick(ActionEvent e) {
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
        BuildManifest manifest = null;
        try {
            // if a manifest exists, load it
            if (gameDir.exists() && new File(gameDir, "manifest.json").exists()) {
                manifest = patcher.loadManifest();
            }

            // if a manifest doesn't exist or isn't valid, download a fresh install of the game and then launch
            if (!gameDir.exists() || !new File(gameDir, "manifest.json").exists() || manifest == null || manifest.exec == null || manifest.version == null) {
                manifest = patcher.freshBuild();
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
            manifest = patcher.updateBuild(manifest);
        } catch (IOException e) {
            LOG.warn("Unable to connect to server");
        } catch (HashMismatchException e) {
            FX.dialog("Hash Mismatch", "Downloaded build but the file was corrupted", Alert.AlertType.ERROR);
            LOG.warn("Hash Missmatch", e);
        }

        // even if patch failed, still run the game
        launchFile(new File(gameDir, manifest.exec));
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

    public void launchFile(File executable) {
        boolean isReadable = executable.setReadable(true);
        boolean isExecutable = executable.setExecutable(true, false);
        LOG.info("Launching Procelio from {} - R:{}, X:{}", executable.getAbsolutePath(), isReadable, isExecutable);

        Server[] servers;
        String hostname;
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
        Process game;
        try {
            LOG.info("Running {} in directory {} - Exists: {}", gameDir.toPath().relativize(executable.toPath()), gameDir, executable.exists());
            game = new ProcessBuilder(executable.getAbsolutePath(), "-IP", hostname, "-PORT", "7777", "-client").directory(gameDir).inheritIO().start();
            Platform.runLater(() -> primaryStage.setIconified(true));
        } catch (IOException e) {
            LOG.error("Unable to launch Procelio", e);
            FX.dialog("Unable to Launch", e.toString(), Alert.AlertType.ERROR);
            return;
        }
        try {
            game.waitFor();
        } catch (InterruptedException e) {
            LOG.warn("Un-Iconify started early", e);
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
            return new Task<>() {
                @Override
                protected Void call() {
                    launchGame();
                    return null;
                }
            };
        }
    }
}
