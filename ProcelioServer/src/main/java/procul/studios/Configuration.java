package procul.studios;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.pojo.response.LauncherConfiguration;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public class Configuration {
    //the unicode bytes for the keystore password stored as base64
    //not really secure, just a bit of extra obfuscation
    //if someone has access to the server filesystem you have bigger problems
    public boolean serverKeepAlive;
    public String keystorePassB64;
    public String keystorePath;
    public int port;
    public String[] serverLocation;
    public String databasePath;
    public String serverKeyB64;
    public String partConfigPath;
    public String buildFolderPath;
    //In Seconds
    public int timeout;
    transient public PartConfiguration partConfig;
    transient public LauncherConfiguration launcherConfig;
    public String launcherConfigPath;

    //use own gson for pretty printing
    static Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static Logger LOG = LoggerFactory.getLogger(Configuration.class);

    public Configuration() {
        serverKeepAlive = false;
        keystorePassB64 = null;
        keystorePath = null;
        databasePath = "";
        port = 80;
        serverLocation = new String[0];
        serverKeyB64 = "";
        buildFolderPath = "";
        partConfigPath = "";
        launcherConfigPath = "";
    }

    public String getKeystorePass(){
        if(keystorePassB64 == null)
            return null;
        return new String(Base64.getDecoder().decode(keystorePassB64), StandardCharsets.UTF_8);
    }

    public String getServerKey(){
        if(serverKeyB64 == null)
            return null;
        return new String(Base64.getDecoder().decode(serverKeyB64), StandardCharsets.UTF_8);
    }

    public static <T> T loadConfiguration(Path path, Class<T> type) throws IOException {
        T config;
        try (Reader reader = Files.newBufferedReader(path)){
            config = gson.fromJson(reader, type);
        } catch (IOException e) {
            throw new IOException("Unable to read configuration file at " + path, e);
        }
        return config;
    }
}
