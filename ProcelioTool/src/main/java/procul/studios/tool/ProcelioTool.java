package procul.studios.tool;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import procul.studios.pojo.StatFile;
import procul.studios.pojo.StatFileBinary;
import procul.studios.tool.command.*;
import procul.studios.util.BytesUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Command(name = "proctool", subcommands = {Dump.class, Pipe.class, Server.class, Create.class, Stats.class},
        mixinStandardHelpOptions = true, description = "Procelio Command Line Tool", versionProvider = ToolVersion.class)
public class ProcelioTool implements Runnable {

    @Option(names = {"-c", "--config"}, description = "The location of a statfile for additional context")
    private String configFile;
    private StatFile stats;

    public static void main(String[] args) {
        CommandLine.run(new ProcelioTool(), args);
    }

    @Override
    public void run() {
        new CommandLine(new ProcelioTool()).usage(System.out);
    }

    public StatFile getConfig() {
        if(configFile == null)
            return null;
        if (stats != null)
            return stats;
        Path source = Paths.get(configFile);
        try {
            byte[] bytes = Files.readAllBytes(source);
            if (BytesUtil.readInt(bytes) == StatFileBinary.MAGIC_NUMBER) {
                stats = StatFile.generate(new StatFileBinary(bytes));
            } else {
                try {
                    stats = new Gson().fromJson(new String(bytes, StandardCharsets.UTF_8), StatFile.class);
                } catch (JsonSyntaxException e1) {
                    throw new IOException(e1);
                }
            }
        } catch (IOException e) {
            return null;
        }
        return stats;
    }
}
