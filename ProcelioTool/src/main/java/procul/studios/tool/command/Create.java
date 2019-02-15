package procul.studios.tool.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import procul.studios.tool.ToolVersion;

@Command(name = "create", versionProvider = ToolVersion.class, description = "Creates a new binary file")
public class Create implements Runnable {
    enum BinaryType {
        INVENTORY, ROBOT, STATFILE
    }

    @Option(names = {"-h", "--help"}, usageHelp = true,
            description = "Print usage help and exit.")
    boolean usageHelpRequested;

    @Override
    public void run() {

    }
}
