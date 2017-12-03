package procul.studios.pojo;

import java.util.*;

public class Part {
    public String partID;
    public Integer cost;
    public Part(String partID) {
        this.partID = partID;
    }

    @Override
    public boolean equals(Object obj) {
        if(Part.class.isAssignableFrom(obj.getClass()))
            return ((Part)obj).partID.equals(partID);
        return false;
    }

    public static Map<String, Integer> combine(Map<String, Integer> inventory, Part[] robot){
        for(Part p : robot){
            inventory.put(p.partID, inventory.getOrDefault(p.partID, 0) + 1);
        }
        return inventory;
    }

    public static Map<String, Integer> extract(Map<String, Integer> inventory, Part[] robot){
        for(Part p : robot){
            inventory.put(p.partID, inventory.getOrDefault(p.partID, 0) - 1);
        }
        return inventory;
    }
}
