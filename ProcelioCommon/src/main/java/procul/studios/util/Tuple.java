package procul.studios.util;

import java.util.Objects;

public class Tuple<V, T> {
    protected V first;
    protected T second;

    public Tuple(V first, T second) {
        this.first = first;
        this.second = second;
    }

    public V getFirst() {
        return first;
    }

    public void setFirst(V first) {
        this.first = first;
    }

    public T getSecond() {
        return second;
    }

    public void setSecond(T second) {
        this.second = second;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null)
            return false;
        if(obj == this)
            return true;
        if(Tuple.class.isAssignableFrom(obj.getClass())){
            Tuple cast = (Tuple) obj;
            return (Objects.equals(first, cast.first)) && (Objects.equals(second, cast.second));
        }
        return false;
    }

    @Override
    public String toString() {
        if(first.getClass().isArray() && first.getClass().getComponentType().equals(byte.class))
            return Hashing.printHexBinary((byte[]) first) + " | " + second.toString();
        return first.toString() + " | " + second.toString();
    }
}
