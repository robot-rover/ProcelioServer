package procul.studios.pojo;

import procul.studios.util.BytesUtil;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Garages {
    private Map<Integer, Robot> garageSlots;
    public Garages(InputStream in) throws IOException {
        int size = BytesUtil.readInt(in);
        garageSlots = new HashMap<>();
        for(int i = 0; i < size; i++) {
            int slotNumber = BytesUtil.readInt(in);
            garageSlots.put(slotNumber, new Robot(in));
        }
    }

    public int getSize() {
        return garageSlots.size();
    }

    public Set<Map.Entry<Integer, Robot>> iterate() {
        return garageSlots.entrySet();
    }

    public Garages(List<Robot> robots) {
        garageSlots = new HashMap<>();
        for(int i = 0; i < robots.size(); i++) {
            garageSlots.put(i, robots.get(i));
        }
    }

    private int nextOpenId(){
        int max = 0;
        for(int id : garageSlots.keySet()){
            if(max < id)
                max = id;
        }
        return max + 1;
    }

    public Robot getRobot(int slot) {
        return garageSlots.get(slot);
    }

    public Robot removeRobot(int slot) {
        return garageSlots.remove(slot);
    }

    public int addRobot(Robot robot) {
        int nextId = nextOpenId();
        garageSlots.put(nextId, robot);
        return nextId;
    }

    public int updateRobot(int slot, Robot robot) {
        garageSlots.put(slot, robot);
        return slot;
    }

    public Garages(byte[] data) throws IOException {
        this(new ByteArrayInputStream(data));
    }

    public byte[] serialize() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        serialize(out);
        return out.toByteArray();
    }

    public void serialize(OutputStream out) throws IOException {
        BytesUtil.writeInt(out, garageSlots.size());
        for(Map.Entry<Integer, Robot> robot : garageSlots.entrySet()) {
            BytesUtil.writeInt(out, robot.getKey());
            robot.getValue().serialize(out);
        }
    }
}
