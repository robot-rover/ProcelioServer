package procul.studios.pojo.response;

import com.google.gson.reflect.TypeToken;
import procul.studios.pojo.Part;
import procul.studios.pojo.Robot;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class User {
    public Integer id;
    public String username;
    public Long currency;
    public Integer userTypeField;
    public Integer xp;
    public String avatar;
    public static final Type inventoryType = new TypeToken<Map<String, Integer>>(){}.getType();
    public Map<String, Integer> inventory;
    public static final Type garageType = new TypeToken<List<Robot>>(){}.getType();
    public List<Robot> garage;

    public boolean rewardCheck(){
        return id == null || currency == null || xp == null;
    }
}
