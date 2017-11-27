package procul.studios.pojo;

import static procul.studios.ProcelioServer.gson;

public class Server {
    public String name;
    public String hostname;
    //0 is US, nonzero (aka 1) is EU
    public Integer region;
    public int usersOnline;
    public int capacity;
    public boolean isOnline;
    public Server(String hostname){
        name = "";
        this.hostname = hostname;
        region = null;
        usersOnline = 0;
        capacity = 0;
        isOnline = false;
    }

    @Override
    public String toString(){
        return gson.toJson(this);
    }
}
