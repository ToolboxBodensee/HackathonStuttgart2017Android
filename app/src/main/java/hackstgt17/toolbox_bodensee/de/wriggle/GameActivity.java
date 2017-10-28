package hackstgt17.toolbox_bodensee.de.wriggle;

import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
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

public class GameActivity extends AppCompatActivity implements ServiceConnection, Game.GameListener {

    private BluetoothDevice bluetoothDevice;
    private MetaWearBoard metaWearBoard;
    private Led led;
    private SensorFusionBosch sensorFusionT;

    private Game game;

    private Button buttonToggleGame;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        buttonToggleGame = findViewById(R.id.buttonToggleGame);

        bluetoothDevice = getIntent().getParcelableExtra(BLEScannerActivity.BLUETOOTH_DEVICE_KEY);

        game = new Game(this, getIntent().getStringExtra(GameSetupActivity.PLAYER_NAME_KEY));

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
        sensorFusionT = metaWearBoard.getModule(SensorFusionBosch.class);
        final SensorFusionBosch sensorFusion = sensorFusionT;
        led = metaWearBoard.getModule(Led.class);
        led.editPattern(Led.Color.BLUE, Led.PatternPreset.PULSE).commit();
        led.play();

        Log.d("GAMESETUP", "Got Bosch sensor fusion");
        sensorFusion.configure()
                .mode(SensorFusionBosch.Mode.NDOF)
                .accRange(SensorFusionBosch.AccRange.AR_2G)
                .gyroRange(SensorFusionBosch.GyroRange.GR_250DPS)
                .commit();
        Log.d("GAMESETUP", "Initializing sensor data stream");
        Log.d("GAMESETUP", "Starting game");
        sensorFusion.eulerAngles().addRouteAsync(source -> source.limit(33).stream(game))
                .continueWith((Continuation<Route, Void>) ignored -> {
                    sensorFusion.eulerAngles().start();
                    sensorFusion.start();
                    Log.d("GAMESETUP", "Initialized game");
                    game.start();
                    return null;
                });
    }

    public void onClickGameToggle(View view) {
        if (!game.isRunning()) {
            game.start();
            buttonToggleGame.setText("Pause Game");
        } else {
            game.stop();
            buttonToggleGame.setText("Start Game");
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
        if (sensorFusionT != null) {
            sensorFusionT.stop();
        }
        game.stop();
        try {
            metaWearBoard.disconnectAsync().waitForCompletion();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ///< Unbind the service when the activity is destroyed
        getApplicationContext().unbindService(this);
        super.onDestroy();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        game.stop();
        Toast.makeText(this, "Sensor disconnected.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisconnect(String message) {
        Log.e("GameDisconnect", message);
        game.stop();
        Toast.makeText(this, "Game disconnected.", Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    public void gameStarting(int color, String name) {

        View v = findViewById(R.id.gameScreen);
        runOnUiThread(() -> v.setBackgroundColor(color));

        ((TextView) findViewById(R.id.textViewStatus)).setText("Go, " + name + "!");
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
}
