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
    public static int port;
    public static boolean useSsl = false;
    public ProcelioServer(){

    }
    public static void main(String[] args){
        if(args.length < 2)
            throw new RuntimeException("Requires 2 arguments");
        databasePath = args[0];
        port = Integer.parseInt(args[1]);
        if(args.length >= 4){
            sslKeystore = args[2];
            keystorePass = args[3];
            useSsl = true;
        }
        Database database = new Database();
        DatabaseWrapper wrapper = new DatabaseWrapper(database.getContext());
        SparkServer server = new SparkServer(port, wrapper);
        server.start();
    }
}
