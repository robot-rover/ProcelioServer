package procul.studios;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import procul.studios.exception.RestException;
import procul.studios.pojo.Server;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Timer;
import java.util.TimerTask;

import static procul.studios.ProcelioServer.gson;

public class ServerDaemon  extends TimerTask {
    private static Logger LOG = LoggerFactory.getLogger(ServerDaemon.class);
    String[] serverLocations;
    Server[] serverStatus;
    boolean debug;

    public ServerDaemon(String[] serverLocations, Server[] serverStatus){
        this(serverLocations, serverStatus, false);
    }

    public ServerDaemon(String[] serverLocations, Server[] serverStatus, boolean debug){
        this.serverStatus = serverStatus;
        this.serverLocations = serverLocations;
        this.debug = debug;
    }

    @Override
    public void run() {
        for(int i = 0; i < serverLocations.length; i++) {
            try {
                HttpResponse<InputStream> response = Unirest.get(serverLocations[i] + "/status").header("Accept", "application/json").asBinary();
                if(response.getStatus() != 200){
                    throw new RestException("Server returned status " + String.valueOf(response.getStatus()) + " with body:\n" + response.getBody());
                }
                try (InputStreamReader in = new InputStreamReader(response.getBody())) {
                    serverStatus[i] = gson.fromJson(in, Server.class);
                }
            } catch (UnirestException | RestException | IOException e) {
                //Don't log every check if the server was already offline
                if(serverStatus[i] == null || serverStatus[i].isOnline){
                    LOG.warn("Lost connection to server at url `" + serverLocations[i] + "`", e);
                }
                serverStatus[i] = new Server(serverLocations[i]);
            }
            if(debug)
                LOG.info(serverStatus[i].toString());
        }
    }

    public static Timer startDaemon(long period, ServerDaemon daemon){
        Timer timer = new Timer("serverPoll", true);
        timer.schedule(daemon,0, period);
        return timer;
    }
}
