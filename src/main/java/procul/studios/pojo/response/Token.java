package procul.studios.pojo.response;

public class Token {
    public String token;
    public int userID;
    public long expires_at;
    public Token(String token, int userID, long expires_at){
        this.token = token;
        this.userID = userID;
        this.expires_at = expires_at;
    }
}
