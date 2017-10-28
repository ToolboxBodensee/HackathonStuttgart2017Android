package de.toolbox_bodensee.hackstgt17.wriggle;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

public class SettingsActivity extends AppCompatActivity {

    public static final String SHAREDPREFS_SERVER = "server_url";
    private EditText editTextServer;
    SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        editTextServer = findViewById(R.id.editTextServer);
        editTextServer.setText(sharedPreferences.getString(SHAREDPREFS_SERVER, "http://wriggle-backend.herokuapp.com/"));

    }

    public void onClickSaveSettings(View view) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(SHAREDPREFS_SERVER, editTextServer.getText().toString());
        editor.apply();
        finish();
    }
}
