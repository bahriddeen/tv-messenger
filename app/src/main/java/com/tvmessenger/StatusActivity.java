package com.tvmessenger;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.TextView;

/** The tile's screen: whether the TV is listening, and when the last message came. Opening it (re)starts the service. */
public final class StatusActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private SharedPreferences prefs;
    private TextView status;
    private TextView last;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status);
        status = findViewById(R.id.status);
        last = findViewById(R.id.last);
        prefs = getSharedPreferences(MessengerService.PREFS, MODE_PRIVATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MessengerService.start(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        refresh();
    }

    @Override
    protected void onPause() {
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        runOnUiThread(this::refresh);
    }

    private void refresh() {
        boolean connected = prefs.getBoolean(MessengerService.KEY_CONNECTED, false);
        status.setText(connected ? R.string.status_connected : R.string.status_not_connected);
        long when = prefs.getLong(MessengerService.KEY_LAST, 0);
        last.setText(when == 0 ? getString(R.string.last_none)
                : getString(R.string.last_at, DateFormat.format("d MMM, HH:mm", when)));
    }
}
