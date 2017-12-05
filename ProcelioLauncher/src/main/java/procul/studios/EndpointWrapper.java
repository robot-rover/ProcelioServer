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
import procul.studios.util.Tuple;
import procul.studios.util.Version;

import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
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
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            return new BufferedInputStream(httpConnection.getInputStream());
            //return new Tuple<>(new BufferedInputStream(new DigestInputStream(httpConnection.getInputStream(),md5)), new Tuple<>(httpConnection.getHeaderField("Content-MD5"), md5));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Can't get input stream", e);
        }
    }

    public InputStream getFile(String path, Consumer<Integer> progressCallback) throws IOException {
            URL url = new URL(path);
            HttpURLConnection httpConnection = (HttpURLConnection) (url.openConnection());
            long completeFileSize = httpConnection.getContentLength();
            File tempFile = File.createTempFile("procelioLauncher", null);
            LOG.info("TempFile for {}: {}", path, tempFile.getAbsoluteFile());
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not supported", e);
        }

        try (BufferedInputStream in = new BufferedInputStream(new DigestInputStream(httpConnection.getInputStream(), md5));
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
            LOG.info("Server: {} -> Client: {}", httpConnection.getHeaderField("Content-MD5"), DatatypeConverter.printHexBinary(md5.digest()));

            return new BufferedInputStream(new FileInputStream(tempFile));
        }
    }

    public Server[] getServers() throws UnirestException {
        HttpResponse<String> response = Unirest.get(backendEndpoint + "/servers").asString();
        return gson.fromJson(response.getBody(), Server[].class);

    }
}
