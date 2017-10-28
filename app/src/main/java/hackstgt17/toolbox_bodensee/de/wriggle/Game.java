package hackstgt17.toolbox_bodensee.de.wriggle;

import android.util.Log;

import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.Subscriber;
import com.mbientlab.metawear.data.EulerAngles;

import java.net.URISyntaxException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * Created by Jonas on 28.10.2017.
 */

public class Game implements Subscriber {

    private boolean running = false;
    private static final String URL = "http://10.200.19.196:3000";
    private Socket socket;
    private float currentOrientation = 0;
    private String playerName = "";
    private GameDisconnectListener gameDisconnectListener;
    private ScheduledThreadPoolExecutor networkLoopExecutor;
    private ScheduledFuture<?> networkLoopFuture;


    public Game(GameDisconnectListener gameDisconnectListener) {

        this.gameDisconnectListener = gameDisconnectListener;

        try {
            socket = IO.socket(URL);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            gameDisconnectListener.onDisconnect("Server URL not valid.");
        }

        networkLoopExecutor = new ScheduledThreadPoolExecutor(1);
    }

    public void setPlayerName(String name) {
        playerName = name;
    }

    private Runnable postDirection = new Runnable() {
        @Override
        public void run() {
            double rad = Math.toRadians(currentOrientation);
            Log.d("GAME", "Posting direction " + rad + " rad(" + currentOrientation + "°) to server");
            try {
                socket.emit("changeDirection", rad);
            } catch (Exception e) {
                gameDisconnectListener.onDisconnect("Connection to server failed.");
            }
        }
    };

    public void start() {
        running = true;
        socket.connect();
        socket.emit("name", playerName);
        networkLoopFuture = networkLoopExecutor.scheduleAtFixedRate(postDirection, 0, 100, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        running = false;
        socket.disconnect();
        if (networkLoopFuture != null) {
            networkLoopFuture.cancel(false);
        }
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Receives rotation sensor data
     *
     * @param data
     * @param env
     */
    @Override
    public void apply(Data data, Object... env) {
        //Log.i("GAME", "Raw Sensor: " + data.toString());
        //Log.i("GAME", "Euler: " + data.value(EulerAngles.class).toString());
        currentOrientation = data.value(EulerAngles.class).roll();
    }

    public interface GameDisconnectListener {
        void onDisconnect(String message);
    }
}
