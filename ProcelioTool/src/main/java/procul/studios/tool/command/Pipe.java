package procul.studios.tool.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import procul.studios.tool.ToolVersion;
import procul.studios.tool.Util;

import java.io.IOException;

@Command(name = "pipe", versionProvider = ToolVersion.class, description = "Dumps information about a binary file piped into stdin")
public class Pipe implements Runnable {

    @Option(names = {"-h", "--help"}, usageHelp = true,
            description = "Print usage help and exit.")
    boolean usageHelpRequested;

    public void run() {
        try {
            byte[] allBytes = Util.readAllBytes(System.in);
            Util.printInfo(allBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}