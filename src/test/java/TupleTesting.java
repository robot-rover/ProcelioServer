import com.google.gson.Gson;
import procul.studios.Configuration;
import procul.studios.Util;

public class TupleTesting {
    public static void main(String[] args){
        Gson gson = new Gson();
        Configuration config = Configuration.loadConfiguration(new java.io.File("config.json"));
        Util.GsonTuple test = new Util.GsonTuple(config, Configuration.class);
        //test is now abstracted and can do expensive serialization somewhere else
        System.out.println(test.serialize(gson));
    }
}
