package procul.studios;

import com.mashape.unirest.http.exceptions.UnirestException;
import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.Patch;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.*;
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
import procul.studios.util.FileUtils;
import procul.studios.util.Hashing;
import procul.studios.util.Tuple;
import procul.studios.util.Version;

import javax.xml.bind.DatatypeConverter;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static procul.studios.EndpointWrapper.gson;

public class ProcelioLauncher extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(ProcelioLauncher.class);
    public static final Version launcherVersion = new Version(0, 0, 0);
    public static final String backendEndpoint = /*"https://api.sovietbot.xyz"*/ "http://localhost";
    public Tuple<Label, ProgressBar> download;
    public TabPane selectView;
    public File gameDir = new File("ProcelioGame");

    public static void main(String[] args) {
        launch(args);
    }

    private EndpointWrapper wrapper;

    @Override
    public void start(Stage primaryStage) throws UnirestException, NoSuchAlgorithmException, CloneNotSupportedException {
        wrapper = new EndpointWrapper();
        primaryStage.setTitle("Procelio Launcher v" + launcherVersion);
        primaryStage.getIcons().add(new Image(ClassLoader.getSystemResourceAsStream("icon.png")));
        BorderPane root = new BorderPane();

        HBox titleBox = new HBox();
        titleBox.setPadding(new Insets(10));
        titleBox.setSpacing(100);
        StackPane logoCollider = new StackPane();
        ImageView logo = new ImageView(new Image(ClassLoader.getSystemResourceAsStream("logo.png")));
        logo.setSmooth(true);
        logo.preserveRatioProperty().setValue(true);
        logo.setFitHeight(100);
        logo.setBlendMode(BlendMode.values()[0]);
        logoCollider.setOnMouseClicked(event -> {
            try {
                openBrowser(wrapper.getConfig().websiteUrl);
            } catch (IOException e) {
                LOG.warn("Unable to connect to server");
                dialog("Connection Error", "Unable to communitcate with Procelio Servers", Alert.AlertType.WARNING);
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

        Tab updateProgress = new Tab();
        updateProgress.setText("Download");
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
        updateProgress.setContent(downloadContent);
        download = new Tuple<>(downloadStatus, downloadProgress);
        selectView.getTabs().add(updateProgress);

        Tab updateList = new Tab();
        updateList.setText("Updates");
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
        if(updates != null)
            for(LauncherConfiguration.Update update : updates){
                HBox entry = new HBox();
                entry.setPadding(new Insets(10));
                entry.setSpacing(10);
                if(update.image != null){
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
                if(update.version != null && update.version.length == 3){
                    Text version = new Text("Version " + update.version[0] + "." + update.version[1] + "." + update.version[2] + "\n");
                    version.setFont(Font.font(20));
                    description.getChildren().add(version);
                }
                description.getChildren().add(new Text(update.description));

                if(update.hyperlink != null){
                    Text link = new Text("\n" + update.hyperlink);
                    link.setOnMouseClicked((MouseEvent event) -> {
                        openBrowser(update.hyperlink);
                    });
                    link.setUnderline(true);
                    description.getChildren().add(link);
                }
                entry.getChildren().add(description);
                updateArea.getChildren().add(entry);


                StackPane lineHolderCopy = new StackPane(new Line(0,0,50,0));
                lineHolder.setAlignment(Pos.CENTER);
                updateArea.getChildren().add(lineHolderCopy);
            }
        updateScroller.contentProperty().setValue(updateArea);
        updateList.setContent(updateScroller);
        selectView.getTabs().add(updateList);
        root.centerProperty().setValue(selectView);

        primaryStage.setScene(new Scene(root, 800, 400));

        primaryStage.show();

    }

    public void dialog(String title, String content, Alert.AlertType type){
        Alert dialog = new Alert(type);
        dialog.setContentText(content);
        dialog.setTitle(title);
        dialog.show();
    }

    public void openBrowser(String url) {
        try {
            if (System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH).contains("nux")) {
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            } else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                LOG.info("Opening " + url);
                Desktop.getDesktop().browse(new URI(url));
            } else {
                //todo: allow copy paste if unsupported
                LOG.warn("Unable to open hyperlink");
            }
        } catch (IOException | URISyntaxException e) {
            LOG.error("Error opening hyperlink " + url, e);
        }
    }

    public void launchClick(ActionEvent e) {
        Thread worker = new Thread(this::launchGame);
        worker.start();
    }

    public void launchGame() {
        try {
            if (!gameDir.exists() || !new File(gameDir, "manifest.json").exists()) {
                freshBuild();
                BuildManifest manifest = EndpointWrapper.gson.fromJson(new InputStreamReader(new FileInputStream(new File(gameDir, "manifest.json"))), BuildManifest.class);
                launchFile(new File(gameDir, manifest.exec));
                return;
            }
        } catch (IOException e) {
            dialog("Connection Error", "Unable to download game\n" + e.getMessage(), Alert.AlertType.ERROR);
            LOG.warn("Error downloading and starting game", e);
            return;
        }
        try {
            BuildManifest manifest = EndpointWrapper.gson.fromJson(new FileReader(new File(gameDir, "manifest.json")), BuildManifest.class);
            try {
                updateBuild(manifest);
            } catch (IOException e){
                LOG.warn("Unable to connect to server");
            }
            launchFile(new File(gameDir, manifest.exec));
        } catch (IOException e){
            dialog("Unable to Lanch", "The launcher encountered an error and is unable to launch Procelio\n" + e.getMessage(), Alert.AlertType.ERROR);
            LOG.warn("Unable to launch game", e);
        }
    }

    public void updateBuild(BuildManifest manifest) throws IOException {
        LauncherDownload download = wrapper.checkForUpdates(new Version(manifest.version));
        if (download.upToDate) {
            LOG.info("All up to date");
            return;
        }
        if (download.patches == null) {
            freshBuild();
            return;
        }
        LOG.info("Patching Build");
        for (String patch : download.patches) {
            InputStream input = wrapper.getFile(backendEndpoint + patch, this::updateProgressBar);
            applyPatch(input, manifest);
        }
    }

    private String previewByteArray(byte[] array){
        if(array.length < 20)
            throw new RuntimeException("Not long enough");
        return DatatypeConverter.printHexBinary(Arrays.copyOfRange(array, 0, 20));
    }

    public void savePatch(InputStream patch, BuildManifest manifest) throws IOException {
        File outputFile = new File("patch");
        outputFile.mkdir();
        extractInputstream(patch, outputFile);
    }

    private void updateProgressBar(double percent){
        Platform.runLater(() -> {download.getSecond().setProgress(percent); LOG.info(String.valueOf(percent));});
    }

    public void applyPatch(InputStream patch, BuildManifest manifest) throws IOException {
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

            for (String hashAndFile : packageManifest.filesAndHashes){
                String hash = hashAndFile.substring(0, 32);
                String file = hashAndFile.substring(32, hashAndFile.length());
                MessageDigest hasher = Hashing.getMessageDigest();
                if(hasher == null) return;
                DigestInputStream digest = new DigestInputStream(new FileInputStream(new File(gameDir, file)), hasher);
                while(digest.read(buffer) != -1){}
                String fileHash = DatatypeConverter.printHexBinary(hasher.digest());
                if(!hash.equals(fileHash)){
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

    public void freshBuild() throws IOException {
        LOG.info("Making fresh build");
        if (!gameDir.exists())
            gameDir.mkdir();
        else
            //todo: ignore ignored
            FileUtils.deleteRecursive(gameDir);
        download.getFirst().setText("Downloading Build");
        InputStream hashes = wrapper.getFile(backendEndpoint + "/launcher/build", this::updateProgressBar);
        extractInputstream(hashes, gameDir);
        //LOG.info("Server: {} -> Client: {}", hashes.getSecond().getFirst(), DatatypeConverter.printHexBinary(hashes.getSecond().getSecond().digest()));
    }

    public void launchFile(File executable) throws IOException {
        LOG.info("Launching " + executable.getAbsolutePath());
        LOG.info(Files.readAllLines(executable.toPath()).toString());
        executable.setExecutable(true, true);
        Server[] servers;
        try {
            servers = wrapper.getServers();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        Process game = new ProcessBuilder(executable.getAbsolutePath(), servers[0].hostname).directory(gameDir).inheritIO().start();
        LOG.info(executable.getAbsolutePath() + " Exited");
    }

    public static void extractInputstream(InputStream stream, File targetDir) throws IOException {
        byte[] buffer = new byte[1024];
        try (ZipInputStream zipStream = new ZipInputStream(stream)) {
            ZipEntry entry = null;
            while ((entry = zipStream.getNextEntry()) != null) {
                String fileName = entry.getName();
                File newFile = new File(targetDir, fileName);
                new File(newFile.getParent()).mkdirs();
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(newFile))) {
                    int len;
                    while ((len = zipStream.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }
            }
        }
    }
}
