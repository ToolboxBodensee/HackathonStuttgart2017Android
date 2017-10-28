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
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;

import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.module.Led;
import com.mbientlab.metawear.module.NeoPixel;
import com.mbientlab.metawear.module.SensorFusionBosch;

import bolts.Continuation;

public class GameActivity extends AppCompatActivity implements ServiceConnection, Game.GameDisconnectListener {

    private BtleService.LocalBinder serviceBinder;
    private BluetoothDevice bluetoothDevice;
    private MetaWearBoard metaWearBoard;

    private Game game = new Game(this);
    private boolean hardwareReady = false;

    private Button buttonToggleGame;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_game);

        buttonToggleGame = findViewById(R.id.buttonToggleGame);

        bluetoothDevice = getIntent().getParcelableExtra(BLEScannerActivity.BLUETOOTH_DEVICE_KEY);
        game.setPlayerName(getIntent().getStringExtra(GameSetupActivity.PLAYER_NAME_KEY));

        ///< Bind the service when the activity is created
        getApplicationContext().bindService(new Intent(this, BtleService.class),
                this, Context.BIND_AUTO_CREATE);

    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        ///< Typecast the binder to the service's LocalBinder class
        serviceBinder = (BtleService.LocalBinder) service;
        metaWearBoard = serviceBinder.getMetaWearBoard(bluetoothDevice);

        //Connect to Board
        metaWearBoard.connectAsync()
                .continueWith(task -> {
                    //After connecting, start game
                    if (!task.isCancelled()) {
                        setupGame(metaWearBoard);
                    }
                    return null;
                });
    }

    void setupGame(MetaWearBoard metaWearBoard) {
        Log.d("GAMESETUP", "Configuring sensor fusion");
        final SensorFusionBosch sensorFusion = metaWearBoard.getModule(SensorFusionBosch.class);
        Led led = metaWearBoard.getModule(Led.class);
        led.editPattern(Led.Color.BLUE, Led.PatternPreset.PULSE).commit(); //TODO LED*/
        led.play();

        Log.d("GAMESETUP", "Got Bosch sensor fusion");
        //TODO Hangs here sometimes
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
                    return null;
                });
        Log.d("GAMESETUP", "Initialized game");

        hardwareReady = true;
    }

    public void onClickGameToggle(View view) {
        if (hardwareReady) {
            if (!game.isRunning()) {
                game.start();
                buttonToggleGame.setText("Pause Game");
            } else {
                game.stop();
                buttonToggleGame.setText("Start Game");
            }
        } else {
            Toast.makeText(this, "Hardware not ready yet.", Toast.LENGTH_SHORT).show();
        }
    }

    public void onClickCalibrate(View view){

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        game.stop();
        Led led = metaWearBoard.getModule(Led.class);
        led.stop(true);
        metaWearBoard.disconnectAsync();
        ///< Unbind the service when the activity is destroyed
        getApplicationContext().unbindService(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        game.stop();
        Toast.makeText(this, "Sensor disconnected.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisconnect(String message) {
        game.stop();
        finish();
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
}
