package procul.studios.tool.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import procul.studios.tool.ProcelioTool;
import procul.studios.tool.ToolVersion;
import procul.studios.tool.Util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Command(name = "dump", versionProvider = ToolVersion.class, description = "Dumps info about a binary file")
public class Dump implements Runnable {

    @ParentCommand
    private ProcelioTool parent;

    @Parameters(paramLabel = "FILE", description = "The file to parse")
    private String filename;

    @Option(names = {"-h", "--help"}, usageHelp = true,
            description = "Print usage help and exit.")
    boolean usageHelpRequested;

    public void run() {
        Path dump = Paths.get(filename);
        try {
            if(!Files.exists(dump)) {
                throw new FileNotFoundException("Cannot find file " + dump);
            }
            byte[] allBytes = Files.readAllBytes(dump);
            Util.printInfo(allBytes, parent.getConfig());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}