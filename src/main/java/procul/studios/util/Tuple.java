package procul.studios.util;

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
            return (first == null ? cast.first == null : first.equals(cast.first)) && (second == null ? cast.second == null : second.equals(cast.second));
        }
        return false;
    }
}
