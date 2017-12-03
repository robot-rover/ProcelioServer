package procul.studios.pojo;

import org.junit.Test;
import procul.studios.pojo.Robot;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RobotTest {
    @Test
    public void setIDShouldCreateNewMax(){
        int[] id1 = {1,2,3,4,5,6};
        int result1 = 7;
        int[] id2 = {4,7,12,43,5,3,2};
        int result2 = 44;
        int[] id3 = {0};
        int result3 = 1;
        Robot r1 = new Robot();
        System.out.println("Testing...");
        assertEquals(result1, r1.setIDtoNext(idToRobot(id1)).id);
        assertEquals(result2, r1.setIDtoNext(idToRobot(id2)).id);
        assertEquals(result3, r1.setIDtoNext(idToRobot(id3)).id);
    }

    public List<Robot> idToRobot(int[] ids){
        Robot[] robots = new Robot[ids.length];
        for(int i = 0; i < robots.length; i++){
            robots[i] = new Robot();
            robots[i].id = ids[i];
        }
        return Arrays.asList(robots);
    }
}
