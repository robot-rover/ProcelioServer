package procul.studios;

import com.google.gson.JsonSyntaxException;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.pojo.Server;
import procul.studios.pojo.response.LauncherConfiguration;
import procul.studios.pojo.response.LauncherDownload;
import procul.studios.util.HashMismatchException;
import procul.studios.util.Hashing;
import procul.studios.util.OperatingSystem;
import procul.studios.util.Version;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Consumer;

import static procul.studios.ProcelioLauncher.backendEndpoint;
import static procul.studios.gson.GsonSerialize.gson;

public class EndpointWrapper {
    private static final Logger LOG = LoggerFactory.getLogger(EndpointWrapper.class);
    LauncherConfiguration config;
    String osHeaderKey = "X-Operating-System";
    OperatingSystem osHeaderValue = OperatingSystem.get();

    public void getBuildFile() {

    }

    public LauncherConfiguration getConfig() throws IOException {
        if(config != null)
            return config;
        LOG.info("Fetching Config");
        HttpResponse<String> response = null;
        try {
            response = Unirest.get(backendEndpoint + "/launcher/config").asString();
            return (config = gson.fromJson(response.getBody(), LauncherConfiguration.class));
        } catch (UnirestException | JsonSyntaxException e) {
            throw new IOException(e);
        }
    }

    public LauncherDownload checkForUpdates(Version currentVersion) throws IOException {
        HttpResponse<String> response = null;
        try {
            response = Unirest.get(backendEndpoint + "/launcher/" + currentVersion).header(osHeaderKey, osHeaderValue.getIndex()).asString();
        } catch (UnirestException e) {
            throw new IOException(e);
        }
        LOG.info("Fetching Updates");
        return gson.fromJson(response.getBody(), LauncherDownload.class);
    }

    public Image getImage(String url) throws IOException {
        LOG.info("Fetching Image {}", url);
        HttpResponse<InputStream> response = null;
        try {
            response = Unirest.get(url).asBinary();
        } catch (UnirestException e) {
            throw new IOException(e);
        }
        return new Image(response.getBody());
    }

    public InputStream getFile(String path) throws IOException, HashMismatchException {
        return getFile(path, null);
    }

    @Deprecated
    public DigestInputStream getInputStream(String path) throws IOException {
        try {
            MessageDigest hash = MessageDigest.getInstance("MD5");
            return new DigestInputStream(Unirest.get(path).asBinary().getBody(), hash);
        } catch (UnirestException e) {
            throw new IOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public InputStream getFile(String path, Consumer<Double> progressCallback) throws IOException, HashMismatchException {
            URL url = new URL(path);
            HttpURLConnection httpConnection = (HttpURLConnection) (url.openConnection());
            httpConnection.setRequestProperty(osHeaderKey, osHeaderValue.getIndex());
            long completeFileSize = httpConnection.getContentLengthLong();
            File tempFile = File.createTempFile("procelioLauncher", null);
            LOG.info("TempFile for {}: {} - Size: {}", path, tempFile.getAbsoluteFile(), completeFileSize);
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not supported", e);
        }

        for(int triesLeft = 3; triesLeft > 0; triesLeft--) {
            String serverHash;
            String clientHash;
            try (BufferedInputStream in = new BufferedInputStream(new DigestInputStream(httpConnection.getInputStream(), md5));
                 BufferedOutputStream buff = new BufferedOutputStream(new FileOutputStream(tempFile, false))) {
                LOG.info("Downloading {} with {} tries left", path, triesLeft);
                byte[] data = new byte[1024*64];
                long downloadedFileSize = 0;
                int x = 0;
                while ((x = in.read(data)) >= 0) {
                    downloadedFileSize += x;

                    // calculate progress
                    final double currentProgress = (double) downloadedFileSize / (double) completeFileSize;
                    // update progress bar
                    if (progressCallback != null)
                        progressCallback.accept(currentProgress);

                    buff.write(data, 0, x);
                }
                serverHash = httpConnection.getHeaderField("Content-MD5");
                clientHash = Hashing.printHexBinary(md5.digest());
                buff.flush();
            }
            LOG.info("Server: {} -> Client: {}", serverHash, clientHash);
            if(serverHash.equals(clientHash))
                return new BufferedInputStream(new FileInputStream(tempFile));
        }
        throw new HashMismatchException(path);
    }

    public Server[] getServers() throws IOException {
        HttpResponse<String> response = null;
        try {
            response = Unirest.get(backendEndpoint + "/servers").asString();
        } catch (UnirestException e) {
            throw new IOException(e);
        }
        return gson.fromJson(response.getBody(), Server[].class);

    }
}
