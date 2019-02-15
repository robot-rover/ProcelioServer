package procul.studios.pojo.response;

public class User {
    public Integer id;
    public String username;
    public Long currency;
    public Integer userTypeField;
    public Integer xp;
    public String avatar;

    public boolean rewardCheck(){
        return id == null || currency == null || xp == null;
    }
}
