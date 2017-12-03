package procul.studios.pojo.request;

public class Authenticate {
    public String username;
    public String password;
    public boolean credCheck(){
        return username == null || password == null;
    }
}
