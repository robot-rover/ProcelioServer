package procul.studios.pojo.response;

public class Message {
    String message;
    Integer code;
    public Message(String message){
        this.message = message;
    }

    public Message(String message, int code){
        this.message = message;
        this.code = code;
    }
}
