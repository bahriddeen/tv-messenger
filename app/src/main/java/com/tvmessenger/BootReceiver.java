package com.tvmessenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts listening after every boot, so nobody has to open the app. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) MessengerService.start(context);
    }
}
