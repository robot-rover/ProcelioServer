package procul.studios;

import spark.Request;
import spark.Response;
import spark.Spark;

/**
 *
 */
//todo: add class description
public class SparkServer {
    int port;
    boolean isIgnited;
    public SparkServer(int port){
        this.port = port;
        isIgnited = false;
    }

    public void start(){
        Spark.port(port);
        Spark.get("/status", this::status);
        Spark.init();
        isIgnited = true;
    }

    public void stop(){
        Spark.stop();
        isIgnited = false;
    }

    public Response status(Request req, Response res) {
        res.status(100);
        return res;
    }
}
