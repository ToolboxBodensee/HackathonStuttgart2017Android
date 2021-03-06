package de.toolbox_bodensee.hackstgt17.wriggle;

import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.module.Led;
import com.mbientlab.metawear.module.SensorFusionBosch;
import com.mbientlab.metawear.module.Settings;

import bolts.Continuation;
import bolts.Task;

/**
 * @author ottojo0802
 */

public class GameActivity extends AppCompatActivity implements ServiceConnection, Game.GameListener {


    private BluetoothDevice bluetoothDevice;
    private MetaWearBoard metaWearBoard;
    private Led led;
    private SensorFusionBosch sensorFusion;

    private Game game;

    private Button gameToggleButton;
    private TextView statusTextView;
    private ConstraintLayout gameScreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        gameToggleButton = findViewById(R.id.buttonToggleGame);
        statusTextView = findViewById(R.id.textViewStatus);
        gameScreen = findViewById(R.id.gameScreen);

        bluetoothDevice = getIntent().getParcelableExtra(BLEScannerActivity.BLUETOOTH_DEVICE_KEY);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String server = prefs.getString(SettingsActivity.SHAREDPREFS_SERVER, "https://wriggle-backend.herokuapp.com/");

        game = new Game(this, getIntent().getStringExtra(GameSetupActivity.PLAYER_NAME_KEY), server);

        ///< Bind the service when the activity is created
        getApplicationContext().bindService(new Intent(this, BtleService.class),
                this, Context.BIND_AUTO_CREATE);

    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        ///< Typecast the binder to the service's LocalBinder class
        BtleService.LocalBinder serviceBinder = (BtleService.LocalBinder) service;
        metaWearBoard = serviceBinder.getMetaWearBoard(bluetoothDevice);

        //Connect to Board
        metaWearBoard.connectAsync().continueWithTask(task -> {
            if (task.isCancelled()) {
                return task;
            }
            return task.isFaulted() ? reconnect(metaWearBoard) : task;
        }).continueWith(task -> {
            //After connecting, start game
            if (!task.isCancelled()) {
                metaWearBoard.getModule(Settings.class).editBleConnParams()
                        .maxConnectionInterval(11.25f)
                        .commit();
                setupGame(metaWearBoard);
            }
            return null;
        }, Task.UI_THREAD_EXECUTOR);
    }

    void setupGame(MetaWearBoard metaWearBoard) {
        Log.d("GAMESETUP", "Configuring sensor fusion");
        sensorFusion = metaWearBoard.getModule(SensorFusionBosch.class);
        final SensorFusionBosch sensorFusionF = sensorFusion;
        led = metaWearBoard.getModule(Led.class);

        Log.d("GAMESETUP", "Got Bosch sensor fusion");
        sensorFusionF.configure()
                .mode(SensorFusionBosch.Mode.IMU_PLUS)
                .accRange(SensorFusionBosch.AccRange.AR_2G)
                .gyroRange(SensorFusionBosch.GyroRange.GR_250DPS)
                .commit();
        Log.d("GAMESETUP", "Initializing sensor data stream");
        Log.d("GAMESETUP", "Starting game");
        sensorFusionF.eulerAngles().addRouteAsync(source -> source.limit(33).stream(game))
                .continueWith((Continuation<Route, Void>) ignored -> {
                    sensorFusionF.eulerAngles().start();
                    sensorFusionF.start();
                    Log.d("GAMESETUP", "Initialized game");
                    game.start();
                    return null;
                });
    }

    public void onClickGameToggle(View view) {
        if (!game.isRunning()) {
            game.start();
            gameToggleButton.setText("Pause Game");

        } else {
            game.stop();
            if (led != null) {
                led.stop(false);
            }
            gameToggleButton.setText("Start Game");
            statusTextView.setText("wait...");
        }
    }

    public void onClickCalibrate(View view) {
        game.calibrate();
    }

    @Override
    protected void onDestroy() {
        if (led != null) {
            led.stop(true);
        }
        if (sensorFusion != null) {
            sensorFusion.stop();
        }
        game.stop();

        metaWearBoard.disconnectAsync();

        ///< Unbind the service when the activity is destroyed
        getApplicationContext().unbindService(this);
        super.onDestroy();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        game.stop();
        Toast.makeText(this, "Sensor disconnected.", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public void onDisconnect(String message) {
        Log.e("GameDisconnect", message);
        if (game != null) {
            game.stop();
        }
        Toast.makeText(this, "Game disconnected.", Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    public void gameStarting(int color, String name) {
        int textColor;
        if (isColorDark(color)) {
            textColor = Color.WHITE;
        } else {
            textColor = Color.BLACK;
        }
        runOnUiThread(() -> statusTextView.setTextColor(textColor));
        runOnUiThread(() -> gameScreen.setBackgroundColor(color));
        runOnUiThread(() -> statusTextView.setText("Go, " + name + "!"));
        if (led != null) {
            led.stop(true);
            byte red = (byte) (Color.red(color) * (31.0/255.0));
            byte green = (byte) (Color.green(color) * (31.0/255.0));
            byte blue = (byte) (Color.blue(color) * (31.0/255.0));
            led.editPattern(Led.Color.RED, Led.PatternPreset.SOLID).highIntensity(red).lowIntensity(red).commit();
            led.editPattern(Led.Color.GREEN, Led.PatternPreset.SOLID).highIntensity(green).lowIntensity(green).commit();
            led.editPattern(Led.Color.BLUE, Led.PatternPreset.SOLID).highIntensity(blue).lowIntensity(blue).commit();
            led.play();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }

    public static Task<Void> reconnect(final MetaWearBoard board) {
        return board.connectAsync()
                .continueWithTask(task -> {
                    if (task.isFaulted()) {
                        return reconnect(board);
                    } else if (task.isCancelled()) {
                        return task;
                    }
                    return Task.forResult(null);
                });
    }

    public boolean isColorDark(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        if (darkness < 0.5) {
            return false; // It's a light color
        } else {
            return true; // It's a dark color
        }
    }
}
