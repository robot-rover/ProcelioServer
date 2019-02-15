package procul.studios;

import org.junit.Test;
import procul.studios.pojo.Inventory;
import procul.studios.pojo.PartTuple;
import procul.studios.util.Hashing;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class InventoryTest {
    @Test
    public void SerializationTest() throws IOException {
        Map<Short, Integer> entries = new HashMap<>();
        entries.put((short) 1, 10);
        entries.put((short) 2, 5);
        entries.put((short) 3, 10);
        entries.put((short) 0xFFFF, Integer.MAX_VALUE);
        entries.put(Short.MIN_VALUE, 10);
        entries.put(Short.MAX_VALUE, 5);
        Inventory expected = new Inventory(entries);
        System.out.println(Hashing.printHexBinary(expected.serialize()));
        Inventory actual = new Inventory(expected.serialize());
        assertEquals("Inventories are not equal", expected, actual);
    }

    @Test
    public void combineExtractTest() {
        Map<Short, Integer> source = new HashMap<>();
        source.put((short) 1, 10);
        source.put((short) 2, 5);
        source.put((short) 3, 10);
        PartTuple[] delta = new PartTuple[17];
        for(int i = 0; i < 5; i++)
            delta[i] = new PartTuple(new byte[3], (byte) 0, new byte[3], (short) 1);
        for(int i = 5; i < 8; i++)
            delta[i] = new PartTuple(new byte[3], (byte) 0, new byte[3], (short) 2);
        for(int i = 8; i < 17; i++)
            delta[i] = new PartTuple(new byte[3], (byte) 0, new byte[3], (short) 3);
        Map<Short, Integer> combine = new HashMap<>();
        combine.put((short) 1, 15);
        combine.put((short) 2, 8);
        combine.put((short) 3, 19);
        Map<Short, Integer> extract = new HashMap<>();
        extract.put((short) 1, 5);
        extract.put((short) 2, 2);
        extract.put((short) 3, 1);

        Inventory ISource = new Inventory(source);
        Inventory ICombine = new Inventory(combine);
        Inventory IExtract = new Inventory(extract);

        assertEquals(ICombine, ISource.combine(delta));
        assertEquals(IExtract, ISource.extract(delta).extract(delta));
    }
}
