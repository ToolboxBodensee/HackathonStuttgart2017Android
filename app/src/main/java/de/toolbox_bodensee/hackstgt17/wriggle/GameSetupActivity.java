package de.toolbox_bodensee.hackstgt17.wriggle;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;

/**
 * @author ottojo0802
 */

public class GameSetupActivity extends AppCompatActivity {

    public static final String PLAYER_NAME_KEY = "playerName";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_setup);
    }

    public void onClickStartGame(View view) {
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(BLEScannerActivity.BLUETOOTH_DEVICE_KEY,
                (Parcelable) getIntent().getParcelableExtra(BLEScannerActivity.BLUETOOTH_DEVICE_KEY));
        intent.putExtra(PLAYER_NAME_KEY,
                ((EditText) findViewById(R.id.editTextPlayerName)).getText().toString());
        startActivity(intent);
    }
}
