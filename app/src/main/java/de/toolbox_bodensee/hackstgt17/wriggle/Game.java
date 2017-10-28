package de.toolbox_bodensee.hackstgt17.wriggle;

import android.graphics.Color;
import android.util.Log;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.Subscriber;
import com.mbientlab.metawear.data.EulerAngles;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * Created by Jonas on 28.10.2017.
 */

public class Game implements Subscriber {

    private boolean running = false;
    private static final String URL = "http://wriggle-backend.herokuapp.com/";
    private Socket socket;
    private float currentOrientation = 0;
    private float orientationOffset = 0;
    private GameListener gameListener;
    private ScheduledThreadPoolExecutor networkLoopExecutor;
    private ScheduledFuture<?> networkLoopFuture;
    private int color;
    private String name;

    public Game(GameListener gameListener, String playerName) {

        this.gameListener = gameListener;
        this.name = playerName;

        try {
            IO.Options options = new IO.Options();
            options.query = "name=" + URLEncoder.encode(playerName, "utf-8");
            socket = IO.socket(URL, options);
            socket.on("connectionSuccess", connectionSuccess);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            gameListener.onDisconnect("Server URL not valid.");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        networkLoopExecutor = new ScheduledThreadPoolExecutor(1);
    }

    private Emitter.Listener connectionSuccess = args -> {
        JSONObject data = (JSONObject) args[0];
        try {
            color = Color.parseColor(data.getString("color"));
            gameListener.gameStarting(color, name);
        } catch (JSONException e) {
            gameListener.onDisconnect("Invalid color.");
        }
    };

    void calibrate() {
        orientationOffset = currentOrientation;
    }

    private Runnable postDirection = new Runnable() {
        @Override
        public void run() {
            double rad = Math.toRadians(currentOrientation - orientationOffset);
            Log.d("GAME", "Posting direction " + (-1 * rad) + " rad(" + (-1 * (currentOrientation - orientationOffset)) + "°) to server");
            try {
                socket.emit("changeDirection", (-1 * rad));
            } catch (Exception e) {
                gameListener.onDisconnect("Connection to server failed.");
            }
        }
    };

    void start() {
        running = true;
        socket.connect();
        networkLoopFuture = networkLoopExecutor.scheduleAtFixedRate(postDirection, 0, 100, TimeUnit.MILLISECONDS);
        Log.d("GAME", "Started");
    }

    void stop() {
        running = false;
        socket.disconnect();
        if (networkLoopFuture != null) {
            networkLoopFuture.cancel(false);
        }
    }

    boolean isRunning() {
        return running;
    }

    /**
     * Receives rotation sensor data
     */
    @Override
    public void apply(Data data, Object... env) {
        currentOrientation = data.value(EulerAngles.class).roll();
    }

    public interface GameListener {
        void onDisconnect(String message);

        void gameStarting(int color, String name);
    }
}
