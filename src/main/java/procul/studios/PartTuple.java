package procul.studios;

public class PartTuple {
    String partID;
    // Vector(X, Y, Z) then EulerRotation(X, Y, Z)
    int[] transform;
    public PartTuple(String partID, int[] positionAndRotation){
        this.partID = partID;
        if(positionAndRotation.length != 6)
            throw new ArrayIndexOutOfBoundsException("Transform array must have 6 elements!");
        this.transform = positionAndRotation;
    }
}