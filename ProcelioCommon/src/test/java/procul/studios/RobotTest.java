package procul.studios;

import org.junit.Test;
import procul.studios.pojo.PartTuple;
import procul.studios.pojo.Robot;
import procul.studios.util.Hashing;

import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class RobotTest {
    @Test
    public void serializeTest() throws IOException {
        Robot robot = new Robot("Idiot Test",
            new byte[] {
                    Byte.MAX_VALUE, Byte.MIN_VALUE, (byte) 0xFF, 0, -1, 6, 7, 10
            },
            new PartTuple[]{
                new PartTuple(new byte[]{1, 2, 3}, (byte) 3, new byte[]{4, 5, 6}, (short) 45),
                new PartTuple(new byte[]{Byte.MAX_VALUE, Byte.MIN_VALUE, 3}, (byte) 0xFF, new byte[]{4, 5, 6}, (short) 0xFFFF)
            }
        );
        Robot translate = new Robot(robot.serialize());
        System.out.println(Hashing.printHexBinary(robot.serialize()));
        assertRobotEquals(robot, translate);
    }

    private static void assertRobotEquals(Robot expected, Robot actual) {
        assertEquals("Names not equal", expected.name, actual.name);
        assertArrayEquals("Metadata not equal", expected.metadata, actual.metadata);
        assertEquals("Part Lists aren't the same length", expected.partList.length, actual.partList.length);
        for(int i = 0; i < expected.partList.length; i++) {
            assertPartTupleEqual(expected.partList[i], actual.partList[i]);
        }
    }

    private static void assertPartTupleEqual(PartTuple expected, PartTuple actual) {
        assertArrayEquals("Transform arrays not equal", expected.transform, actual.transform);
        assertEquals("Rotations not equal", expected.rotation, actual.rotation);
        assertArrayEquals("Color arrays not equal", expected.color, actual.color);
        assertEquals("PartIds not equal", expected.partId, actual.partId);
    }
}
