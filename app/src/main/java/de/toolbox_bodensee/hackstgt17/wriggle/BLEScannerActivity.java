package de.toolbox_bodensee.hackstgt17.wriggle;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.mbientlab.bletoolbox.scanner.BleScannerFragment;

import java.util.UUID;

public class BLEScannerActivity extends AppCompatActivity implements BleScannerFragment.ScannerCommunicationBus {

    public static final String BLUETOOTH_DEVICE_KEY = "bluetoothDevice";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blescanner);
    }

    @Override
    public UUID[] getFilterServiceUuids() {
        //Only match mbientlab devices
        return new UUID[]{UUID.fromString("326a9000-85cb-9195-d9dd-464cfbbae75a")};
    }

    @Override
    public long getScanDuration() {
        return 10000;
    }

    @Override
    public void onDeviceSelected(BluetoothDevice device) {
        Intent intent = new Intent(this, GameSetupActivity.class);
        intent.putExtra(BLUETOOTH_DEVICE_KEY, device);
        startActivity(intent);
    }
}
