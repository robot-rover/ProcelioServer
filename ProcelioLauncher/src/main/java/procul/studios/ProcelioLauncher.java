package procul.studios;

import com.mashape.unirest.http.exceptions.UnirestException;
import com.sun.xml.internal.bind.v2.runtime.reflect.Lister;
import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;
import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.Patch;
import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.BlendMode;
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
import procul.studios.util.Version;
import spark.utils.IOUtils;

import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static procul.studios.EndpointWrapper.gson;

public class ProcelioLauncher extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(ProcelioLauncher.class);
    public static final Version launcherVersion = new Version(0,0,0);
    public static final String backendEndpoint = /*"https://api.sovietbot.xyz"*/ "http://localhost";
    public File gameDir = new File("ProcelioGame");

    public static void main(String[] args) {
        launch(args);
    }

    private EndpointWrapper wrapper;
    private int shaderOrdinal = 0;

    @Override
    public void start(Stage primaryStage) throws UnirestException {
        wrapper = new EndpointWrapper();
        primaryStage.setTitle("Procelio Launcher v" + launcherVersion);
        primaryStage.getIcons().add(wrapper.getIcon());
        BorderPane root = new BorderPane();

        HBox titleBox = new HBox();
        titleBox.setPadding(new Insets(10));
        titleBox.setSpacing(100);
        StackPane logoCollider = new StackPane();
        ImageView logo = new ImageView(wrapper.getLogo());
        logo.setSmooth(true);
        logo.preserveRatioProperty().setValue(true);
        logo.setFitHeight(100);
        logo.setBlendMode(BlendMode.values()[0]);
        logoCollider.setOnMouseClicked(event -> {
            try {
                openBrowser(wrapper.getConfig().websiteUrl);
            } catch (UnirestException e) {
                e.printStackTrace();
            }
        });
        logoCollider.setOnMouseEntered(event -> logo.setBlendMode(BlendMode.DIFFERENCE));
        logoCollider.setOnMouseExited(event -> logo.setBlendMode(null));
        logoCollider.getChildren().add(logo);
        titleBox.getChildren().add(logoCollider);
        VBox motd = new VBox();
        motd.setPadding(new Insets(10));
        motd.setAlignment(Pos.CENTER_RIGHT);
        Label motdText = new Label(wrapper.getConfig().quoteOfTheDay);
        motdText.setWrapText(true);
        motd.getChildren().add(motdText);
        Label motdAuthor = new Label("- " + wrapper.getConfig().quoteAuthor);
        motdAuthor.setAlignment(Pos.TOP_RIGHT);
        motd.getChildren().add(motdAuthor);
        titleBox.getChildren().add(motd);
        HBox.setHgrow(motd, Priority.ALWAYS);
        root.topProperty().setValue(titleBox);

        HBox launchBox = new HBox();
        launchBox.setPadding(new Insets(10));
        launchBox.setAlignment(Pos.CENTER_RIGHT);
        Button launch = new Button("Launch");
        launch.setOnAction(this::launch);
        launchBox.getChildren().add(launch);
        root.bottomProperty().setValue(launchBox);

        ScrollPane content = new ScrollPane();
        content.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        content.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        content.fitToWidthProperty().setValue(true);
        VBox updateArea = new VBox();
        updateArea.setPadding(new Insets(10));
        updateArea.setSpacing(10);
        List<LauncherConfiguration.Update> updates = wrapper.getConfig().updates;
        StackPane lineHolder = new StackPane(new Line(0,0,50,0));
        lineHolder.setAlignment(Pos.CENTER);
        updateArea.getChildren().add(lineHolder);
        for(LauncherConfiguration.Update update : updates){
            HBox entry = new HBox();
            entry.setPadding(new Insets(10));
            entry.setSpacing(10);
            if(update.image != null){
                ImageView updateIcon = new ImageView(wrapper.getImage(update.image));
                updateIcon.setSmooth(true);
                updateIcon.preserveRatioProperty().setValue(true);
                updateIcon.setFitHeight(100);
                entry.getChildren().add(updateIcon);
            }
            TextFlow description = new TextFlow();
            if(update.version != null && update.version.length == 3){
                Text version = new Text("Version " + update.version[0] + "." + update.version[1] + "." + update.version[2] + "\n");
                version.setFont(Font.font(20));
                description.getChildren().add(version);
            }
            description.getChildren().add(new Text(update.description));
            //description.setWrapText(true);
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
        content.contentProperty().setValue(updateArea);
        root.centerProperty().setValue(content);

        primaryStage.setScene(new Scene(root, 800, 400));

        primaryStage.show();
    }

    public void openBrowser(String url){
        try {
            if(System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH).contains("nux")){
                Runtime.getRuntime().exec(new String[] { "xdg-open", url});
            } else if(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)){
                LOG.info("Opening " + url);
                Desktop.getDesktop().browse(new URI(url));
            } else {
                LOG.warn("No way to open hyperlink");
            }
        } catch (IOException | URISyntaxException e) {
            LOG.error("Error opening hyperlink " + url, e);
        }
    }

    public void launch(ActionEvent e) {
        try {
            boolean isReady = false;
            if(!gameDir.exists()){
                freshBuild();
                isReady = true;
            }
            BuildManifest manifest = EndpointWrapper.gson.fromJson(new InputStreamReader(new FileInputStream(new File(gameDir, "manifest.json"))), BuildManifest.class);
            if(!isReady)
                updateBuild(manifest);
            launchFile(new File(gameDir, manifest.exec));
        } catch (UnirestException | IOException e1) {
            throw new RuntimeException(e1);
        }
    }

    public void updateBuild(BuildManifest manifest) throws UnirestException, IOException {
        LauncherDownload download = wrapper.checkForUpdates(new Version(manifest.version));
        if(download.patches == null){
            freshBuild();
            return;
        }
        for(String patch : download.patches){
            applyPatch(wrapper.getInputStream(backendEndpoint + patch), manifest);
        }
    }

    public void applyPatch(InputStream patch, BuildManifest manifest) throws IOException {
        byte[] buffer = new byte[1024];
        PackageManifest packageManifest;
        try (ZipInputStream zipStream = new ZipInputStream(patch)){
            ZipEntry entry = zipStream.getNextEntry();
            if(!entry.getName().equals("manifest.json"))
                throw new RuntimeException("manifest.json must be the first zip entry");
                LOG.info("Loading Package Manifest");
                try (ByteArrayOutputStream manifestData = new ByteArrayOutputStream()) {
                    readEntry(zipStream, buffer, manifestData);
                    packageManifest = gson.fromJson(new String(manifestData.toByteArray()), PackageManifest.class);
                    if(packageManifest.delete == null)
                        packageManifest.delete = new ArrayList<>();
                }
                while ((entry = zipStream.getNextEntry()) != null) {
                String fileName = entry.getName();
                if(FileUtils.getFileExtension(fileName).equals("patch")){
                    String gameDirFile = fileName.substring(0, fileName.length() - ".patch".length());
                    File toPatch = new File(gameDir, gameDirFile);
                    if(!toPatch.exists()){
                        LOG.warn("File is missing {}", toPatch.getAbsolutePath());
                        continue;
                    }
                    byte[] oldBytes = Files.readAllBytes(gameDir.toPath().resolve(gameDirFile));
                    try (ByteArrayOutputStream patchStream = new ByteArrayOutputStream()) {
                        readEntry(zipStream, buffer, patchStream);
                        OutputStream patchedOut = new BufferedOutputStream(new FileOutputStream(toPatch, false));
                        Patch.patch(oldBytes, patchStream.toByteArray(), patchedOut);
                    } catch (InvalidHeaderException | CompressorException e) {
                        LOG.error("Patch Error", e);
                    }
                } else {
                    if(packageManifest.delete.contains(fileName)){
                        LOG.info("Ignoring {}" + fileName);
                    }
                    File newFile = new File(gameDir, fileName);
                    new File(newFile.getParent()).mkdirs();
                    if(newFile.isDirectory())
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

            for(String toDeletePath : packageManifest.delete){
                File toDelete = new File(gameDir, toDeletePath);
                if(toDelete.exists() && !toDelete.delete())
                    LOG.warn("Cannot delete file {}", toDelete.getAbsolutePath());

            }
        }
    }

    private void readEntry(ZipInputStream zip, byte[] buffer, OutputStream out) throws IOException {
        int len;
        while ((len = zip.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
    }

    public void freshBuild() throws IOException, UnirestException {
        if(!gameDir.exists())
            gameDir.mkdir();
        else
            FileUtils.deleteRecursive(gameDir);
        extractInputstream(wrapper.getInputStream(backendEndpoint + "/launcher/build"), gameDir);
    }

    public void launchFile(File executable) throws IOException {
        Server[] servers;
        try {
            servers = wrapper.getServers();
        } catch (UnirestException e) {
            e.printStackTrace();
            return;
        }
        executable.setExecutable(true, true);
        Process game = new ProcessBuilder(executable.getAbsolutePath(), servers[0].hostname).directory(gameDir).inheritIO().start();
    }

    public static void extractInputstream(InputStream stream, File targetDir) throws IOException {
        byte[] buffer = new byte[1024];
        try (ZipInputStream zipStream = new ZipInputStream(stream)){
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
