package procul.studios;

import org.junit.Assert;
import org.junit.Test;
import procul.studios.pojo.StatFileBinary;
import procul.studios.pojo.StatFileBinary.Block;
import procul.studios.pojo.StatFileBinary.Flag;

import java.io.IOException;

public class StatFileTest {
    @Test
    public void serializeTest() throws IOException {
        StatFileBinary expected = new StatFileBinary(new Block[]{
                new Block((short) 8, new Flag[]{
                        new Flag(Byte.MIN_VALUE, Integer.MIN_VALUE)
                }),
                new Block(Short.MAX_VALUE, new Flag[]{
                        new Flag((byte) 1, 1003323239)
                }),
                new Block((short) -14004, new Flag[]{
                        new Flag(Byte.MAX_VALUE, 10)
                }),
                new Block(Short.MIN_VALUE, new Flag[]{
                        new Flag((byte) 1, Integer.MAX_VALUE)
                }),
        });

        StatFileBinary actual = new StatFileBinary(expected.serialize());
    }

    @Test
    public void sanityCheck() {
        StatFileBinary expected = new StatFileBinary(new Block[]{
                new Block((short) 8, new Flag[]{
                        new Flag(Byte.MIN_VALUE, Integer.MIN_VALUE)
                }),
                new Block(Short.MAX_VALUE, new Flag[]{
                        new Flag((byte) 1, 1003323239)
                }),
                new Block((short) -14004, new Flag[]{
                        new Flag(Byte.MAX_VALUE, 10)
                }),
                new Block(Short.MIN_VALUE, new Flag[]{
                        new Flag((byte) 1, Integer.MAX_VALUE)
                }),
        });

        assertStatFileEquals(expected, expected);
    }

    private void assertStatFileEquals(StatFileBinary expected, StatFileBinary actual) {
        Assert.assertEquals("Array Lengths differ", expected.blocks.length, actual.blocks.length);
        for(int i = 0; i < expected.blocks.length; i++) {
            Block blockExpected = expected.blocks[i];
            Block blockActual = actual.blocks[i];
            Assert.assertEquals("Part IDs differ", blockExpected.partId, blockActual.partId);
            Assert.assertEquals("Array Lengths differ", blockExpected.flags.length, blockActual.flags.length);
            for(int j = 0; j < blockExpected.flags.length; j++) {
                Flag flagExpected = blockExpected.flags[j];
                Flag flagActual = blockActual.flags[j];
                Assert.assertEquals("Flag Types Differ", flagExpected.type, flagActual.type);
                Assert.assertEquals("Flag Values Differ", flagExpected.value, flagActual.value);
            }
        }
    }
}
