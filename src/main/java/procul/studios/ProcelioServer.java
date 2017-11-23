package procul.studios;

/**
 *
 */
//todo: add class description
public class ProcelioServer {
    public static String url = "http://api.sovietbot.xyz";
    public static String databasePath;
    public static String sslKeystore;
    public static String keystorePass;
    public static boolean useSsl = false;
    public ProcelioServer(){

    }
    public static void main(String[] args){
        if(args.length < 1)
            throw new RuntimeException("Requires 1 argument");
        databasePath = args[0];
        if(args.length >= 3){
            sslKeystore = args[1];
            keystorePass = args[2];
            useSsl = true;
        }
        Database database = new Database();
        DatabaseWrapper wrapper = new DatabaseWrapper(database.getContext());
        SparkServer server = new SparkServer(100, wrapper);
        server.start();
    }
}
