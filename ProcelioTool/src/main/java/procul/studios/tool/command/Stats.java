package procul.studios.tool.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import procul.studios.gson.StatFile;
import procul.studios.pojo.StatFileBinary;
import procul.studios.tool.ToolVersion;
import procul.studios.util.BytesUtil;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Command(name = "stats", versionProvider = ToolVersion.class, description = "Converts between statfiles between binary and json")
public class Stats implements Runnable {
    @Parameters(paramLabel = "INPUT", description = "The input file to read", index = "0")
    String inputFile;

    @Parameters(paramLabel = "OUTPUT", description = "Location to write the converted file", index = "1")
    String outputFile;


    @Override
    public void run() {
        try {
            Path source = Paths.get(inputFile);
            byte[] bytes = Files.readAllBytes(source);
            if (BytesUtil.readInt(bytes) == StatFileBinary.MAGIC_NUMBER) {
                readBinary(bytes);
            } else {
                try {
                    StatFile statFileSource = new Gson().fromJson(new String(bytes, StandardCharsets.UTF_8), StatFile.class);
                    readGson(statFileSource);
                } catch (JsonSyntaxException e) {
                    System.out.println(inputFile + " -> format is not recognized");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void readBinary(byte[] bytes) throws IOException {
        StatFileBinary statFile = new StatFileBinary(bytes);
        Path target = Paths.get(outputFile);
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
            out.write(new GsonBuilder().setPrettyPrinting().create().toJson(StatFile.generate(statFile)).getBytes(StandardCharsets.UTF_8));
        }
    }

    private void readGson(StatFile statFileSource) throws IOException {
        Path target = Paths.get(outputFile);
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
            statFileSource.export().serialize(out);
        }
    }
}
