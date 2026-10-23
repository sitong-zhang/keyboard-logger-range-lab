package com.keyrecord.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Minimal transparent activity whose only job is to request the RECORD_AUDIO
 * permission at runtime (an InputMethodService cannot call requestPermissions).
 * Used by the keyboard's microphone button for on-device/system speech dictation.
 * No network is used — recognition is delegated to the system speech service.
 */
public class PermissionActivity extends AppCompatActivity {
    private static final int REQ = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            finish();
            return;
        }
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO}, REQ);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        Toast.makeText(this,
                granted ? "麦克风已授权，请再次点击键盘上的麦克风"
                        : "未授权麦克风，语音输入不可用",
                Toast.LENGTH_SHORT).show();
        finish();
    }
}
