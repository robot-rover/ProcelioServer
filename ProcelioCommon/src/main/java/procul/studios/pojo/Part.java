package procul.studios.pojo;


public class Part {
    public short partId;
    public int cost;

    @Override
    public boolean equals(Object obj) {
        if(Part.class.isAssignableFrom(obj.getClass()))
            return ((Part)obj).partId == partId;
        return false;
    }
}
