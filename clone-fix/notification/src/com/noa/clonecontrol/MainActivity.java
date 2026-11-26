package com.noa.clonecontrol;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;

public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        startForegroundService(new Intent(this, ControlService.class));
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 64, 48, 48);
        TextView title = new TextView(this);
        title.setText("AI Clone\nКонтроль i2v и i2i\nУправление доступно и в постоянном уведомлении.");
        title.setTextSize(21);
        layout.addView(title);
        for (String action : new String[]{"ON", "OFF"}) {
            Button button = new Button(this);
            button.setText(action.equals("ON") ? "Включить" : "Выключить");
            button.setOnClickListener(view -> startForegroundService(new Intent(this, ControlService.class).setAction(action)));
            layout.addView(button);
        }
        setContentView(layout);
    }
}
