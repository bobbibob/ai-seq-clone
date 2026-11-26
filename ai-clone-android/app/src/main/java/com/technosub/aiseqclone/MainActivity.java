package com.technosub.aiseqclone;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Switch autoRunSwitch;
    private Button runAutomationButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        autoRunSwitch = findViewById(R.id.autoRunSwitch);
        runAutomationButton = findViewById(R.id.runAutomationButton);

        runAutomationButton.setOnClickListener(v -> {
            if (autoRunSwitch.isChecked()) {
                Intent intent = new Intent(this, MainService.class);
                intent.putExtra("auto_run", true);
                startService(intent);
            } else {
                // Just run once
                Intent intent = new Intent(this, MainService.class);
                intent.putExtra("auto_run", false);
                startService(intent);
            }
        });
    }

    private void showToast(final String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }
}
