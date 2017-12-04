package procul.studios;

import com.google.gson.Gson;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.pojo.Server;
import procul.studios.pojo.response.LauncherConfiguration;
import procul.studios.pojo.response.LauncherDownload;
import procul.studios.util.Version;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Consumer;

import static procul.studios.ProcelioLauncher.backendEndpoint;

public class EndpointWrapper {
    static final Gson gson = new Gson();
    private static final Logger LOG = LoggerFactory.getLogger(EndpointWrapper.class);
    LauncherConfiguration config;

    public LauncherConfiguration getConfig() throws UnirestException {
        if(config != null)
            return config;
        LOG.info("Fetching Config");
        HttpResponse<String> response = Unirest.get(backendEndpoint + "/launcher/config").asString();
        return (config = gson.fromJson(response.getBody(), LauncherConfiguration.class));
    }

    public LauncherDownload checkForUpdates(Version currentVersion) throws UnirestException {
        HttpResponse<String> response = Unirest.get(backendEndpoint + "/launcher/" + currentVersion).asString();
        LOG.info("Fetching Updates");
        return gson.fromJson(response.getBody(), LauncherDownload.class);
    }

    public Image getLogo() throws UnirestException {
        return getImage(ProcelioLauncher.backendEndpoint + "/launcher/logo");
    }

    public Image getIcon() throws UnirestException {
        return getImage(ProcelioLauncher.backendEndpoint + "/launcher/icon");
    }

    public Image getImage(String url) throws UnirestException {
        LOG.info("Fetching Image {}", url);
        HttpResponse<InputStream> response = Unirest.get(url).asBinary();
        return new Image(response.getBody());
    }

    public InputStream getFile(String path) throws IOException {
        return getFile(path, null);
    }

    public InputStream getInputStream(String path) throws IOException {
        URL url = new URL(path);
        HttpURLConnection httpConnection = (HttpURLConnection) (url.openConnection());
        long completeFileSize = httpConnection.getContentLength();
        File tempFile = File.createTempFile("procelioLauncher", null);
        return new BufferedInputStream(httpConnection.getInputStream());
    }

    public InputStream getFile(String path, Consumer<Integer> progressCallback) throws IOException {
            URL url = new URL(path);
            HttpURLConnection httpConnection = (HttpURLConnection) (url.openConnection());
            long completeFileSize = httpConnection.getContentLength();
            File tempFile = File.createTempFile("procelioLauncher", null);

        try (BufferedInputStream in = new BufferedInputStream(httpConnection.getInputStream());
             BufferedOutputStream buff = new BufferedOutputStream(new FileOutputStream(tempFile))) {

            byte[] data = new byte[1024];
            long downloadedFileSize = 0;
            int x = 0;
            while ((x = in.read(data, 0, 1024)) >= 0) {
                downloadedFileSize += x;

                // calculate progress
                final int currentProgress = (int) ((((double) downloadedFileSize) / ((double) completeFileSize)) * 100000d);

                // update progress bar
                if(progressCallback != null)
                    progressCallback.accept(currentProgress);

                buff.write(data, 0, x);
            }
            buff.close();
            in.close();
            return new BufferedInputStream(new FileInputStream(tempFile));
        }
    }

    public Server[] getServers() throws UnirestException {
        HttpResponse<String> response = Unirest.get(backendEndpoint + "/servers").asString();
        return gson.fromJson(response.getBody(), Server[].class);

    }
}
