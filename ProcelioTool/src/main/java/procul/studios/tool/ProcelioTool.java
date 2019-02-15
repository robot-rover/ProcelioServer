package procul.studios.tool;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import procul.studios.tool.command.Create;
import procul.studios.tool.command.Dump;
import procul.studios.tool.command.Pipe;
import procul.studios.tool.command.Server;

@Command(name = "proctool",subcommands = {Dump.class, Pipe.class, Server.class, Create.class}, mixinStandardHelpOptions = true, description = "Procelio Command Line Tool", versionProvider = ToolVersion.class)
public class ProcelioTool implements Runnable {

    public static void main(String[] args) {
        CommandLine.run(new ProcelioTool(), args);
    }

    @Override
    public void run() {
        new CommandLine(new ProcelioTool()).usage(System.out);
    }
}
