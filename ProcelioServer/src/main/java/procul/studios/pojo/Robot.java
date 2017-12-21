package procul.studios.pojo;

import java.util.List;

public class Robot {
    public PartTuple[] partList;
    public String name;
    public int id;
    public Robot(String name, PartTuple[] partList){
        this.name = name;
        this.partList = partList;
    }

    public Robot(){}

    public Robot setIDtoNext(List<Robot> current){
        int max = 0;
        for(Robot robot : current){
            if(max < robot.id)
                max = robot.id;
        }
        this.id = ++max;
        return this;
    }
}
