package procul.studios;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.pojo.response.LauncherConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
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
    public PartConfiguration partConfig;
    public LauncherConfiguration launcherConfig;
    public String launcherConfigPath;

    //use own gson for pretty printing
    static Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static Logger LOG = LoggerFactory.getLogger(Configuration.class);

    public Configuration() {
        partConfigPath = "";
        keystorePassB64 = null;
        keystorePath = null;
        databasePath = "";
        port = 80;
        serverLocation = new String[0];
        serverKeyB64 = "";
        buildFolderPath = "";
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

    public static Configuration loadConfiguration(File file) {
        Configuration config;
        try (FileReader reader = new FileReader(file)){
            config = gson.fromJson(reader, Configuration.class);
        } catch (FileNotFoundException e) {
            LOG.error("Configuration file does not exist at {}, using default...", file.getAbsolutePath());
            config = new Configuration();
        } catch (IOException e) {
            LOG.error("Unable to read configuration file at " + file.getAbsolutePath() + ", using default...", e);
            config = new Configuration();
        }
        return config;
    }
}
