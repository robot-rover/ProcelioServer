package procul.studios.util;

import com.google.gson.Gson;

public class StringSerialize implements GsonSerialize {
    String message;
    public StringSerialize(String message){this.message = message;}
    @Override
    public String serialize(Gson gson) {
        return message;
    }
}
