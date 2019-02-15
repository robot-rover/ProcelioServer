package procul.studios.tool.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import procul.studios.tool.ToolVersion;
import procul.studios.tool.Util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static picocli.CommandLine.Option;

@Command(name = "dump", versionProvider = ToolVersion.class, description = "Dumps info about a binary file")
public class Dump implements Runnable {
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
            Util.printInfo(allBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}