package procul.studios;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import javafx.scene.image.Image;
import org.apache.tools.ant.types.Commandline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.gson.LauncherConfiguration;
import procul.studios.pojo.response.PatchList;
import procul.studios.util.GameVersion;
import procul.studios.util.HashMismatchException;
import procul.studios.util.Hashing;
import procul.studios.util.OperatingSystem;

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
    public OperatingSystem osHeaderValue = OperatingSystem.get();

    public String[] getLauncherArguments(boolean dev) throws IOException {
        HttpResponse<String> response = null;
        try {
            response = Unirest.get(backendEndpoint + "/launcher/args?dev={dev}").routeParam("dev", dev ? "true" : "false").asString();
        } catch (UnirestException e) {
            throw new IOException(e);
        }
        LOG.info("Fetching Arguments");
        return Commandline.translateCommandline(response.getBody());
    }

    public LauncherConfiguration getConfig() throws IOException {
        if (config != null)
            return config;
        LOG.info("Fetching Configuration:  " + backendEndpoint + "/launcher/config");
        HttpResponse<String> response = null;
        try {
            response = Unirest.get(backendEndpoint + "/launcher/config").asString();
            LOG.info("Config got");
            return (config = gson.fromJson(response.getBody(), LauncherConfiguration.class));
        } catch (Exception e) {
            LOG.error("Failed tog et config: " + e);
            throw new IOException(e);
        }
    }

    public PatchList checkForUpdates(GameVersion currentVersion, boolean devOptIn) throws IOException {
        HttpResponse<String> response = null;
        try {
            response = Unirest.get(backendEndpoint + "/game/update?version=" + currentVersion + "&dev=" + devOptIn).
                    header(osHeaderKey, osHeaderValue.getHeaderValue()).asString();
        } catch (UnirestException e) {
            throw new IOException(e);
        }
        LOG.info("Fetching Updates  " + currentVersion);
        LOG.info("resp:"+response.getBody());
        PatchList toRet = gson.fromJson(response.getBody(), PatchList.class);
        LOG.info("Local: "+currentVersion+"; Current: " + toRet.update_to +";  update = " + !currentVersion.equals(toRet.update_to));
        toRet.upToDate = currentVersion.equals(toRet.update_to);
        return toRet;
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

    public String getFileHash(String path) throws IOException {
        HttpResponse<String> response = null;

        if (path.contains("?"))
            path = path.substring(0, path.indexOf('?')) + "/hash" + path.substring(path.indexOf('?'));
        else
            path += "/hash";
        try {
            response = Unirest.get(path).header(osHeaderKey, osHeaderValue.getHeaderValue()).asString();
        } catch (UnirestException e) {
            throw new IOException(e);
        }
        return response.getBody();
    }

    public long getFileSize(String path) throws IOException {
        HttpResponse<String> response = null;
LOG.info("Getting...");
        if (path.contains("?"))
            path = path.substring(0, path.indexOf('?')) + "/size" + path.substring(path.indexOf('?'));
        else
            path += "/size";
        try {
            LOG.info(path+"   " + osHeaderValue.getHeaderValue());
            response = Unirest.get(path).header(osHeaderKey, osHeaderValue.getHeaderValue()).asString();
        } catch (UnirestException e) {
            throw new IOException(e);
        }
        return response.getStatus() == 200 ? Long.parseLong(response.getBody()) : -1;
    }

    public GameVersion getCurrentVersion(boolean devBuild) throws IOException {
        HttpResponse<String> response = null;

        try {
            response = Unirest.get(backendEndpoint + "/game/current?dev="+(devBuild?"true":"false"))
                    .header(osHeaderKey, osHeaderValue.getHeaderValue()).asString();
            LOG.info("Current version: "+response.getBody());
            return gson.fromJson(response.getBody(), GameVersion.class);
        } catch (UnirestException e) {
            throw new IOException(e);
        }
    }

    public InputStream getFile(String path, Consumer<Double> progressCallback) throws IOException, HashMismatchException {
        for (int triesLeft = 3; triesLeft > 0; triesLeft--) {
            URL url = new URL(path);
            HttpURLConnection httpConnection = (HttpURLConnection) (url.openConnection());
            httpConnection.setRequestProperty(osHeaderKey, osHeaderValue.getHeaderValue());
            long completeFileSize = httpConnection.getContentLengthLong();
            File tempFile = File.createTempFile("procelioLauncher", null);
            tempFile.deleteOnExit();
            LOG.info("TempFile for {}: {} - Size: {}", path, tempFile.getAbsoluteFile(), completeFileSize);
            MessageDigest sha512 = null;
            try {
                sha512 = MessageDigest.getInstance("SHA-512");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("SHA256 not supported", e);
            }

            String serverHash;
            String clientHash;
            try (BufferedInputStream in = new BufferedInputStream(new DigestInputStream(httpConnection.getInputStream(), sha512));
                 BufferedOutputStream buff = new BufferedOutputStream(new FileOutputStream(tempFile, false))) {
                LOG.info("Downloading {} with {} tries left", path, triesLeft);
                byte[] data = new byte[1024 * 64];
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
                LOG.info("download done");
                serverHash = getFileHash(path);
                clientHash = Hashing.printHexBinary(sha512.digest());
                buff.flush();
            }
            LOG.info("Server: {} -> Client: {}", serverHash, clientHash);
            if (serverHash == null || serverHash.toLowerCase().equals(clientHash.toLowerCase()))
                return new BufferedInputStream(new FileInputStream(tempFile));
        }
        throw new HashMismatchException(path);
    }
}
