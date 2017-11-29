package procul.studios;

import com.google.gson.Gson;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.function.Supplier;

public class Util {
    public static <V> V findEqualOrInsert(List<V> list, V toFind, Supplier<V> toInsert){
        for(V element : list){
            if(element.equals(toFind))
                return element;
        }
        V inserted = toInsert.get();
        list.add(inserted);
        return inserted;
    }

    public static class GsonTuple {
        Object pojo;
        Class type;
        public GsonTuple(Object pojo, Class type){
            this.pojo = pojo;
            this.type = type;
        }

        public String serialize(Gson gson){
            return gson.toJson(pojo, type);
        }
    }
}
