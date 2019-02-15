package procul.studios.gson;

import com.google.gson.Gson;

import java.lang.reflect.Type;

public class GsonTuple implements GsonSerialize {
    Object pojo;
    Class clazz;
    Type type;

    public GsonTuple(Object pojo, Class clazz){
        this.pojo = pojo;
        this.clazz = clazz;
    }

    public GsonTuple(Object pojo, Type type){
        this.pojo = pojo;
        this.type = type;
    }

    public String serialize(Gson gson){
        if(clazz != null)
            return gson.toJson(pojo, clazz);
        return gson.toJson(pojo, type);
    }
}
