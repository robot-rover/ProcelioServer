package procul.studios.tool.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import procul.studios.tool.ToolVersion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

@Command(name = "server", versionProvider = ToolVersion.class, description = "Checks on the status of a user-server")
public class Server implements Runnable {
    @Option(names = {"--address"}, description = "IP Address to query, or the production server by default", paramLabel = "ADDRESS", defaultValue = "https://www.sovietbot.xyz:8443")
    private String serverAddress = "https://www.sovietbot.xyz:8443";

    @Option(names = {"-h", "--help"}, usageHelp = true,
            description = "Print usage help and exit.")
    boolean usageHelpRequested;

    public void run() {
        try {
            URL url = new URL(serverAddress + "/status");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            System.out.println(con.getResponseCode() + " - " + readString(con.getInputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String readString(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder builder = new StringBuilder();
        String inputLine;
        while ((inputLine = reader.readLine()) != null) {
            builder.append(inputLine);
        }
        return builder.toString();
    }
}