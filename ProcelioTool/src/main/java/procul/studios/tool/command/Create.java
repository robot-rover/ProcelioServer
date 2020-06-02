package procul.studios.tool.command;

import com.google.gson.GsonBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import procul.studios.pojo.Inventory;
import procul.studios.pojo.PartTuple;
import procul.studios.pojo.Robot;
import procul.studios.gson.*;
import procul.studios.tool.ToolVersion;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Command(name = "create", versionProvider = ToolVersion.class, description = "Creates a new binary file")
public class Create implements Runnable {
    enum BinaryType {
        INV, ROBOT, STAT
    }

    @Parameters(paramLabel = "TYPE", description = "The type of binary file to create", index = "0")
    BinaryType fileType;

    @Parameters(paramLabel = "OUTPUT", description = "The file to output to", index = "1")
    String filename;

    @Option(names = {"-h", "--help"}, usageHelp = true,
            description = "Print usage help and exit.")
    boolean usageHelpRequested;

    @Override
    public void run() {
        Path output = Paths.get(filename);
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(output))){
            switch (fileType) {
                case ROBOT:
                    createRobot(out);
                    break;
                case INV:
                    createInventory(out);
                    break;
                case STAT:
                    createStatfile(out);
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }

    private void createStatfile(OutputStream out) throws IOException {
        Scanner scanner = new Scanner(System.in);
        StatFile statFileSource = new StatFile();
        List<StatFile.Block> blocks = new ArrayList<>();
        while (true) {
            Short partId = getPartIdOrBreak(scanner);
            if(partId == null)
                break;
            StatFile.Block block = new StatFile.Block();
            block.id = partId;
            System.out.print("Block Name > ");
            block.name = scanner.nextLine();
            block.health = getIntOrNull(scanner, "Health");
            block.mass = getIntOrNull(scanner, "Mass");
            block.cost = getIntOrNull(scanner, "Cost");
            block.roboRanking = getIntOrNull(scanner, "RoboRanking");
            block.cpuCost = getIntOrNull(scanner, "Cpu Cost");
            block.thrust = getIntOrNull(scanner, "Thrust");
            block.rotationSpeed = getIntOrNull(scanner, "Rotation Speed");
            blocks.add(block);
        }

        statFileSource.blocks = blocks.toArray(new StatFile.Block[0]);
        out.write(new GsonBuilder().setPrettyPrinting().create().toJson(statFileSource).getBytes(StandardCharsets.UTF_8));
    }

    private void createInventory(OutputStream out) throws IOException {
        Scanner scanner = new Scanner(System.in);
        Map<Short, Integer> parts = new HashMap<>();
        while (true) {
            Short partId = getPartIdOrBreak(scanner);
            if(partId == null)
                break;
            int quantity = getInt(scanner, "Quantity");
            parts.put(partId, quantity);
        }

        Inventory inventory = new Inventory(parts);
        inventory.serialize(out);
    }

    private void createRobot(OutputStream out) throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Robot Name > ");
        String name = scanner.nextLine();
        List<PartTuple> parts = new ArrayList<>();
        while (true) {
            Short partId = getPartIdOrBreak(scanner);
            if (partId == null)
                break;
            byte[] transform = getByteVector(scanner, "Enter Transform - vector");
            byte rotation = getByte(scanner, "Enter Rotation - byte");
            byte[] color = getByteVector(scanner, "Enter Color - vector");

            parts.add(new PartTuple(transform, rotation, color, partId));
        }

        Robot robot = new Robot(name, parts.toArray(new PartTuple[0]));
        robot.serialize(out);
    }

    private Short getPartIdOrBreak(Scanner scanner) {
        Short partId = null;
        while (partId == null) {
            System.out.print("Next Part ID (blank exits) > ");
            try {
                String input = scanner.nextLine();
                if(input.isEmpty())
                    return null;
                int parsed = Integer.parseInt(input);
                if((parsed & ~0xFFFF) != 0)
                    throw new NumberFormatException(parsed + " is too large for a short");
                partId = (short) parsed;
            } catch (NumberFormatException e) {
                System.out.println("Invalid Part ID: " + e.getMessage());
            }
        }
        return partId;
    }

    private static final Pattern vectorPattern = Pattern.compile("(\\d*) (\\d*) (\\d*)");
    private byte[] getByteVector(Scanner scanner, String message) {
        byte[] vector = null;
        while(vector == null) {
            System.out.print(message + " > ");
            String input = scanner.nextLine();
            try {
                Matcher m = vectorPattern.matcher(input);
                if(!m.find()) {
                    throw new NumberFormatException();
                }
                vector = new byte[]{parseByte(m.group(1)), parseByte(m.group(2)), parseByte(m.group(3))};
            } catch (NumberFormatException e) {
                System.out.println(input + " is not a valid vector: " + e.getMessage());
            }
        }
        return vector;
    }

    private byte parseByte(String string) throws NumberFormatException {
        int parsed = Integer.parseInt(string);
        if((parsed & ~0xFF) != 0) {
            throw new NumberFormatException(parsed + " is too large for a byte");
        }
        return (byte) parsed;
    }

    private byte getByte(Scanner scanner, String message) {
        Byte datum = null;
        while(datum == null) {
            System.out.print(message + " > ");
            String input = scanner.nextLine();
            try {
                datum = parseByte(input);
            } catch (NumberFormatException e) {
                System.out.println(input + " is not a valid vector: " + e.getMessage());
            }
        }
        return datum;
    }

    private int getInt(Scanner scanner, String message) {
        Integer datum = null;
        while(datum == null) {
            System.out.print(message + " > ");
            String input = scanner.nextLine();
            try {
                datum = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println(input + " is not a valid vector: " + e.getMessage());
            }
        }
        return datum;
    }

    private Integer getIntOrNull(Scanner scanner, String message) {
        while(true) {
            System.out.print(message + " > ");
            String input = scanner.nextLine();
            if(input.isEmpty())
                return null;
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println(input + " is not a valid vector: " + e.getMessage());
            }
        }
    }
}
