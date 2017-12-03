package procul.studios.pojo;

import procul.studios.pojo.Part;

public class PartTuple extends Part {
    // Vector(X, Y, Z) then EulerRotation(X, Y, Z)
    public int[] transform;
    public PartTuple(String partID, int[] positionAndRotation){
        super(partID);
        if(positionAndRotation.length != 6)
            throw new ArrayIndexOutOfBoundsException("Transform array must have 6 elements!");
        this.transform = positionAndRotation;
    }
}