package procul.studios.gson;

import com.google.gson.Gson;

public interface GsonSerialize {
    Gson gson = new Gson();

    String serialize(Gson gson);
}
