package com.myvu.client.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.myvu.client.R;
import com.myvu.client.core.Prefs;
import com.myvu.client.feature.FeatureRegistry;
import com.myvu.client.feature.GlassesFeature;
import com.myvu.client.music.MusicLyricsFeature;
import com.myvu.client.service.ConnectionManager;
import com.myvu.client.service.ConnectionState;
import com.myvu.client.service.MyvuService;

/**
 * Host activity. It owns the connection lifecycle and delegates all features to
 * a {@link FeatureRegistry}. Adding a new capability later only needs:
 *   1. implement {@link GlassesFeature}
 *   2. registry.add(new YourFeature()) in {@link #onCreate(Bundle)}
 */
public class MusicActivity extends AppCompatActivity {

    private static final int REQ_BT = 7;

    private EditText txtMac;
    private TextView txtStatus, txtLog;
    private MyvuService service;
    private boolean bound;
    private final FeatureRegistry registry = new FeatureRegistry();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable statePoll = new Runnable() {
        @Override public void run() {
            refreshState();
            ui.postDelayed(this, 800);
        }
    };
    private final StringBuilder logBuilder = new StringBuilder();
    private boolean lastConnected;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((MyvuService.LocalBinder) binder).getService();
            bound = true;
            contextFeature.ctxConnection = service.connection();
            refreshState();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            bound = false;
            contextFeature.ctxConnection = null;
            registry.setConnected(false);
        }
    };

    /** FeatureContext implementation backed by this Activity + the bound service. */
    private final class HostContext implements GlassesFeature.FeatureContext {
        ConnectionManager ctxConnection;
        @Override public Context appContext() { return MusicActivity.this; }
        @Override public ConnectionManager connection() { return ctxConnection; }
        @Override public boolean isConnected() {
            ConnectionManager c = ctxConnection;
            return c != null && c.isRelayConnected();
        }
        @Override public void runOnUi(Runnable r) { ui.post(r); }
        @Override public void log(String line) { appendLog(line); }
    }

    private final HostContext contextFeature = new HostContext();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music);

        txtMac = findViewById(R.id.txtMac);
        txtStatus = findViewById(R.id.txtStatus);
        txtLog = findViewById(R.id.txtLog);
        Button btnConnect = findViewById(R.id.btnConnect);
        Button btnDisconnect = findViewById(R.id.btnDisconnect);

        String savedMac = Prefs.targetMac(this);
        if (!savedMac.isEmpty()) txtMac.setText(savedMac);

        // ---- register features (add future features here) ----
        LinearLayout container = findViewById(R.id.featureContainer);
        MusicLyricsFeature music = new MusicLyricsFeature();
        registry.add(music);
        View featureView = music.createView(this);
        if (featureView != null) container.addView(featureView);

        registry.attachAll(contextFeature);

        btnConnect.setOnClickListener(v -> connect());
        btnDisconnect.setOnClickListener(v -> disconnect());

        appendLog("就绪。请连接眼镜后加载音乐与歌词。");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent i = new Intent(this, MyvuService.class);
        bindService(i, conn, Context.BIND_AUTO_CREATE);
        ui.post(statePoll);
    }

    @Override
    protected void onStop() {
        ui.removeCallbacks(statePoll);
        registry.setConnected(false);
        if (bound) { unbindService(conn); bound = false; }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        registry.detachAll();
        super.onDestroy();
    }

    // ------------------------------------------------------------ connection

    private void connect() {
        String mac = txtMac.getText() == null ? "" : txtMac.getText().toString().trim();
        if (mac.isEmpty()) {
            Toast.makeText(this, "请输入眼镜 MAC 地址", Toast.LENGTH_SHORT).show();
            return;
        }
        Prefs.setTargetMac(this, mac);
        if (!hasBtPermissions()) {
            ActivityCompat.requestPermissions(this, btPermissions(), REQ_BT);
            return;
        }
        Intent i = new Intent(this, MyvuService.class);
        i.setAction(MyvuService.ACTION_START);
        i.putExtra(MyvuService.EXTRA_MAC, mac);
        startForegroundServiceSafe(i);
        appendLog("正在连接 " + mac + " ...");
    }

    private void disconnect() {
        Intent i = new Intent(this, MyvuService.class);
        i.setAction(MyvuService.ACTION_STOP);
        startService(i);
        appendLog("已请求断开");
    }

    private void startForegroundServiceSafe(Intent i) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
    }

    private boolean hasBtPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED;
    }

    private String[] btPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN};
        }
        return new String[0];
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            connect();
        } else {
            Toast.makeText(this, "需要蓝牙权限才能连接眼镜", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshState() {
        ConnectionManager c = contextFeature.ctxConnection;
        ConnectionState st = (c == null) ? null : c.state();
        boolean connected = c != null && c.isRelayConnected();
        txtStatus.setText(st == null ? "未连接" : (connected ? "已连接 · 可推送歌词" : describe(st)));
        if (connected != lastConnected) {
            lastConnected = connected;
            registry.setConnected(connected);
            appendLog(connected ? "眼镜连接就绪" : "眼镜连接已断开");
        }
    }

    private static String describe(ConnectionState s) {
        if (s == null) return "未连接";
        try { return s.name(); } catch (Exception e) { return "连接中"; }
    }

    private void appendLog(String line) {
        logBuilder.append(line).append('\n');
        if (logBuilder.length() > 2000) {
            logBuilder.delete(0, logBuilder.length() - 2000);
        }
        if (txtLog != null) txtLog.setText(logBuilder.toString());
    }
}
