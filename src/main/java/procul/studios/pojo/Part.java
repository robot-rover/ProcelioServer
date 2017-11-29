package procul.studios.pojo;

public class Part {
    public Integer quantity;
    public String partID;
    public Integer cost;
    public Part(String partID, int quantity){
        this.partID = partID;
        this.quantity = quantity;
    }


    @Override
    public boolean equals(Object obj) {
        if(Part.class.isAssignableFrom(obj.getClass()))
            return ((Part)obj).partID.equals(partID);
        return false;
    }
}
