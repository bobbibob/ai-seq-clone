package com.noa.clonecontrol;

import android.content.*;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        context.startForegroundService(new Intent(context, ControlService.class));
    }
}
