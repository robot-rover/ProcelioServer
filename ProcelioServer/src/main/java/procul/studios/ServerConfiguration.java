package procul.studios;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.gson.Configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

public class ServerConfiguration extends Configuration {
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
    /*transient public PartConfiguration partConfig;
    transient public LauncherConfiguration launcherConfig;
    transient public StatFileBinary statFile;*/
    public String launcherConfigPath;
    public String statFileSource;

    //use own gson for pretty printing
    static Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static Logger LOG = LoggerFactory.getLogger(ServerConfiguration.class);

    public ServerConfiguration() {
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
        statFileSource = "";
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

    public static ServerConfiguration loadConfiguration(Path path) throws IOException {
        return loadGenericConfiguration(path, ServerConfiguration.class);
    }
}
