package procul.studios;

/**
 *
 */
//todo: add class description
public class ProcelioServer {
    public static String keystorePass;
    public static String url = "http://sbtesting.ddns.net";
    public ProcelioServer(){

    }
    public static void main(String[] args){
        if(args.length < 1)
            throw new RuntimeException("Requires 1 argument");
        keystorePass = args[0];
        Database database = new Database();
        DatabaseWrapper wrapper = new DatabaseWrapper(database.getContext());
        SparkServer server = new SparkServer(80, wrapper);
        server.start();
    }
}
