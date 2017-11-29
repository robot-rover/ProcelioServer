package procul.studios.pojo;

import procul.studios.PartTuple;

public class Robot {
    PartTuple[] partList;
    String name;
    //Integer[] is x,y,z
    public Robot(String name, PartTuple[] partList){
        this.name = name;
        this.partList = partList;
    }
}
