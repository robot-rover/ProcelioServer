package procul.studios.util;

import com.google.gson.Gson;

public interface GsonSerialize {
    Gson gson = new Gson();

    String serialize(Gson gson);
}
