package procul.studios.gson;

import procul.studios.pojo.StatFileBinary;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatFile extends Configuration {

    public String getBlockName(Short partId) {
        for(Block block : blocks) {
            if(block.id == partId) {
                return block.name;
            }
        }
        return null;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface TypeId {
        byte value();
    }

    public static class Block {
        public String name;
        public short id;
        @TypeId(0)
        public Integer health;
        @TypeId(1)
        public Integer mass;
        @TypeId(2)
        public Integer cost;
        @TypeId(3)
        public Integer roboRanking;
        @TypeId(4)
        public Integer cpuCost;
        @TypeId(5)
        public Integer thrust;
        @TypeId(6)
        public Integer rotationSpeed;

        public StatFileBinary.Block export() {
            List<StatFileBinary.Flag> flagList = new ArrayList<>();
            for(Field field : Block.class.getDeclaredFields()) {
                TypeId type = field.getDeclaredAnnotation(TypeId.class);
                if(type == null)
                    continue;
                try {
                    Integer val = (Integer) field.get(this);
                    if(val == null)
                        continue;
                    flagList.add(new StatFileBinary.Flag(type.value(), val));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            return new StatFileBinary.Block(id, flagList.toArray(new StatFileBinary.Flag[0]));
        }

        public static Block generate(StatFileBinary.Block block) {
            Map<Byte, Field> map = new HashMap<>();
            for(Field field : Block.class.getDeclaredFields()) {
                TypeId type = field.getDeclaredAnnotation(TypeId.class);
                if(type == null)
                    continue;
                map.put(type.value(), field);
            }
            Block gen = new Block();
            gen.id = block.partId;
            for(StatFileBinary.Flag flag : block.flags) {
                try {
                    map.get(flag.type).set(gen, flag.value);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }

            return gen;
        }
    }

    public Block getPart(short partId){
        for(Block part : blocks){
            if(part.id == partId)
                return part;
        }
        return null;
    }

    public Block[] blocks;

    public StatFileBinary export() {
        StatFileBinary.Block[] rawBlocks = new StatFileBinary.Block[blocks.length];
        for(int i = 0; i < blocks.length; i++) {
            rawBlocks[i] = blocks[i].export();
        }
        return new StatFileBinary(rawBlocks);
    }

    public static StatFile generate(StatFileBinary statFile) {
        StatFile gen = new StatFile();
        gen.blocks = new Block[statFile.blocks.length];
        for(int i = 0; i < gen.blocks.length; i++) {
            gen.blocks[i] = Block.generate(statFile.blocks[i]);
        }
        return gen;
    }

    public static StatFile loadConfiguration(String path) throws IOException {
        return loadGenericConfiguration(path, StatFile.class);
    }
}
