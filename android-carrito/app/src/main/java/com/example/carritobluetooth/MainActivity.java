package com.example.carritobluetooth;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_BT = 20;
    private static final int MOTION_INTERVAL_MS = 100;
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothAdapter adapter;
    private BluetoothSocket socket;
    private volatile OutputStream output;
    private Spinner devices;
    private TextView status, speedLabel, directionLabel;
    private SeekBar speedBar;
    private JoystickView joystick;
    private final ArrayList<BluetoothDevice> bonded = new ArrayList<>();
    private volatile boolean connecting;
    private volatile boolean destroyed;
    private final Handler holdHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService sender = Executors.newSingleThreadExecutor();
    private final MovementTracker movementTracker = new MovementTracker();
    private MovementHistory history;
    private volatile String connectionSession = "", connectedName = "Carrito", connectedAddress = "";
    private String heldCommand;
    private volatile int motionRevision;
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (heldCommand == null) return;
            sendMotion(heldCommand);
            holdHandler.postDelayed(this, MOTION_INTERVAL_MS);
        }
    };

    private void stopMotion() {
        heldCommand = null;
        holdHandler.removeCallbacks(heartbeat);
        if (joystick != null) joystick.reset();
        if (directionLabel != null) directionLabel.setText("EN REPOSO");
        sendMotion("S");
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        history = MovementHistory.get(this);
        adapter = BluetoothAdapter.getDefaultAdapter();
        buildUi();
        if (adapter == null) { status.setText("Este teléfono no tiene Bluetooth clásico"); return; }
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BT);
        } else refreshDevices();
    }

    private int dp(int value) { return NeonTheme.dp(this, value); }
    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1, -2); }

    private LinearLayout panel() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(NeonTheme.panel(this, NeonTheme.PANEL, NeonTheme.BORDER, 20));
        return card;
    }

    private void buildUi() {
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NeonTheme.BACKGROUND);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout branding = new LinearLayout(this);
        branding.setOrientation(LinearLayout.VERTICAL);
        TextView title = NeonTheme.text(this, "Carrito", 25, NeonTheme.TEXT);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        branding.addView(title, full());
        TextView subtitle = NeonTheme.text(this, "CONTROL BLUETOOTH", 10, NeonTheme.CYAN);
        subtitle.setLetterSpacing(.17f);
        branding.addView(subtitle, full());
        heading.addView(branding, new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = NeonTheme.text(this, "NEON / 1.4", 11, NeonTheme.CYAN);
        badge.setTypeface(Typeface.MONOSPACE);
        badge.setPadding(dp(10), dp(8), dp(10), dp(8));
        badge.setBackground(NeonTheme.panel(this, NeonTheme.PANEL, NeonTheme.BORDER, 10));
        heading.addView(badge);
        LinearLayout.LayoutParams headingParams = full();
        headingParams.bottomMargin = dp(12);
        root.addView(heading, headingParams);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout settings = new LinearLayout(this);
        settings.setOrientation(LinearLayout.VERTICAL);
        LinearLayout connection = panel();
        status = NeonTheme.text(this, "●  Desconectado", 13, NeonTheme.CYAN);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        connection.addView(status, full());
        devices = new Spinner(this);
        devices.setContentDescription("Dispositivo Bluetooth emparejado");
        devices.setBackgroundTintList(ColorStateList.valueOf(NeonTheme.CYAN));
        connection.addView(devices, full());
        LinearLayout connectionActions = new LinearLayout(this);
        Button refresh = NeonTheme.button(this, "Buscar", false);
        refresh.setContentDescription("Actualizar dispositivos Bluetooth emparejados");
        refresh.setOnClickListener(v -> { stopMotion(); refreshDevices(); });
        Button connect = NeonTheme.button(this, "Conectar", true);
        connect.setOnClickListener(v -> { stopMotion(); connectSelected(); });
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        refreshParams.rightMargin = dp(8);
        connectionActions.addView(refresh, refreshParams);
        connectionActions.addView(connect, new LinearLayout.LayoutParams(0, dp(48), 1));
        connection.addView(connectionActions, full());
        settings.addView(connection, full());

        LinearLayout velocity = panel();
        LinearLayout.LayoutParams velocityParams = full();
        velocityParams.topMargin = dp(10);
        settings.addView(velocity, velocityParams);
        speedLabel = NeonTheme.text(this, "VELOCIDAD   55%", 13, NeonTheme.CYAN);
        velocity.addView(speedLabel, full());
        speedBar = new SeekBar(this);
        speedBar.setContentDescription("Velocidad del carrito en porcentaje");
        speedBar.setMax(100);
        speedBar.setProgress(55);
        speedBar.setProgressTintList(ColorStateList.valueOf(NeonTheme.CYAN));
        speedBar.setThumbTintList(ColorStateList.valueOf(NeonTheme.CYAN));
        speedBar.setProgressBackgroundTintList(ColorStateList.valueOf(NeonTheme.BORDER));
        velocity.addView(speedBar, new LinearLayout.LayoutParams(-1, dp(40)));
        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int progress, boolean user) {
                speedLabel.setText("VELOCIDAD   " + progress + "%");
            }
            public void onStartTrackingTouch(SeekBar b) { stopMotion(); }
            public void onStopTrackingTouch(SeekBar b) { stopMotion(); }
        });
        if (landscape) {
            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.addView(settings);
            LinearLayout.LayoutParams controls = new LinearLayout.LayoutParams(
                    dp(Math.min(360, Math.max(230, getResources().getConfiguration().screenWidthDp * 38 / 100))), -1);
            controls.rightMargin = dp(12);
            body.addView(scroll, controls);
        } else if (getResources().getConfiguration().screenHeightDp < 600
                || getResources().getConfiguration().fontScale > 1.3f) {
            // Reserve real joystick space on short screens; settings remain scrollable.
            ScrollView scroll = new ScrollView(this);
            scroll.addView(settings);
            LinearLayout.LayoutParams controls = new LinearLayout.LayoutParams(-1,
                    dp(Math.max(120, getResources().getConfiguration().screenHeightDp * 34 / 100)));
            controls.bottomMargin = dp(10);
            body.addView(scroll, controls);
        } else {
            LinearLayout.LayoutParams controls = full();
            controls.bottomMargin = dp(10);
            body.addView(settings, controls);
        }

        LinearLayout drive = panel();
        drive.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout driveHeading = new LinearLayout(this);
        driveHeading.setGravity(Gravity.CENTER_VERTICAL);
        TextView driveTitle = NeonTheme.text(this, "JOYSTICK", 11, NeonTheme.MUTED);
        driveTitle.setLetterSpacing(.13f);
        driveHeading.addView(driveTitle, new LinearLayout.LayoutParams(0, -2, 1));
        directionLabel = NeonTheme.text(this, "EN REPOSO", 12, NeonTheme.CYAN);
        directionLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        driveHeading.addView(directionLabel, new LinearLayout.LayoutParams(-2, -2));
        drive.addView(driveHeading, full());
        joystick = new JoystickView(this);
        joystick.setListener(this::onJoystickDirection);
        drive.addView(joystick, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView help = NeonTheme.text(this, "Arrastra para mover · suelta para detener", 12, NeonTheme.MUTED);
        help.setGravity(Gravity.CENTER);
        drive.addView(help, full());
        body.addView(drive, landscape ? new LinearLayout.LayoutParams(0, -1, 1)
                : new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout footer = new LinearLayout(this);
        Button stop = NeonTheme.button(this, "■  DETENER", true);
        stop.setContentDescription("Parada inmediata del carrito");
        stop.setOnClickListener(v -> stopMotion());
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        stopParams.rightMargin = dp(10);
        footer.addView(stop, stopParams);
        Button historyButton = NeonTheme.button(this, "Historial  ↗", false);
        historyButton.setContentDescription("Ver historial de movimientos en Firebase");
        historyButton.setOnClickListener(v -> {
            stopMotion();
            startActivity(new Intent(this, HistoryActivity.class));
        });
        footer.addView(historyButton, new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams footerParams = full();
        footerParams.topMargin = dp(12);
        root.addView(footer, footerParams);
        NeonTheme.window(this, root);
    }

    private void onJoystickDirection(String command) {
        holdHandler.removeCallbacks(heartbeat);
        heldCommand = null;
        directionLabel.setText(command.equals("S") ? "EN REPOSO"
                : MovementTracker.direction(command).toUpperCase(new java.util.Locale("es")));
        if (command.equals("S")) { sendMotion("S"); return; }
        if (output == null) { status.setText("Conecta primero el carrito"); return; }
        if (speedBar.getProgress() == 0) { directionLabel.setText("VELOCIDAD EN 0%"); sendMotion("S"); return; }
        heldCommand = command;
        holdHandler.post(heartbeat);
    }

    private boolean hasConnectPermission() {
        return Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void refreshDevices() {
        if (adapter == null) return;
        if (!hasConnectPermission()) { requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BT); return; }
        if (!adapter.isEnabled()) { status.setText("Activa Bluetooth en los ajustes del teléfono"); return; }
        bonded.clear(); ArrayList<String> names = new ArrayList<>();
        try { Set<BluetoothDevice> set = adapter.getBondedDevices(); for (BluetoothDevice d : set) { bonded.add(d); names.add(d.getName() + " — " + d.getAddress()); } }
        catch (SecurityException e) { status.setText("Permiso Bluetooth denegado"); }
        if (names.isEmpty()) names.add("Empareja primero el módulo en Ajustes Bluetooth");
        devices.setAdapter(NeonTheme.spinnerAdapter(this, names.toArray(new String[0])));
    }

    private void connectSelected() {
        if (connecting) return;
        if (output != null) { status.setText("El carrito ya está conectado"); return; }
        if (!hasConnectPermission()) { requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BT); return; }
        if (bonded.isEmpty()) { status.setText("No hay dispositivos emparejados"); return; }
        int pos = devices.getSelectedItemPosition(); if (pos < 0 || pos >= bonded.size()) return;
        BluetoothDevice device = bonded.get(pos); connecting = true; status.setText("Conectando…");
        new Thread(() -> {
            BluetoothSocket candidate = null;
            try {
                candidate = device.createRfcommSocketToServiceRecord(SPP_UUID); candidate.connect();
                if (destroyed) { candidate.close(); return; }
                connectedName = device.getName() == null ? "Carrito" : device.getName();
                connectedAddress = device.getAddress();
                connectionSession = UUID.randomUUID().toString();
                socket = candidate; output = candidate.getOutputStream();
                if (destroyed) { candidate.close(); output = null; return; }
                runOnUiThread(() -> {
                    if (!isDestroyed()) { stopMotion(); status.setText("●  Conectado a " + connectedName); }
                });
            } catch (IOException | SecurityException ex) {
                if (candidate != null) try { candidate.close(); } catch (IOException ignored) {}
                runOnUiThread(() -> status.setText("No conectó. Revisa el emparejamiento y la alimentación."));
            } finally { connecting = false; }
        }).start();
    }

    private void sendMotion(String motion) {
        final int revision = ++motionRevision;
        OutputStream stream = output;
        if (stream == null) { if (!motion.equals("S")) status.setText("Conecta primero el carrito"); return; }
        int progress = speedBar.getProgress();
        int speed = motion.equals("S") || progress == 0 ? 0 : Math.max(30, progress * 255 / 100);
        if (speed == 0) motion = "S";
        final String sentMotion = motion, sessionId = connectionSession;
        final String deviceName = connectedName, deviceAddress = connectedAddress;
        final int sentSpeed = speed;
        byte[] bytes = (motion + "," + speed + "\n").getBytes(StandardCharsets.US_ASCII);
        if (sender.isShutdown()) return;
        sender.execute(() -> {
            // Descartar movimientos pendientes al soltar o cambiar de orden.
            if (revision != motionRevision || stream != output) return;
            try {
                stream.write(bytes); stream.flush();
                if (movementTracker.shouldRecord(sentMotion, sentSpeed, sessionId)) {
                    history.record(sentMotion, sentSpeed, System.currentTimeMillis(), deviceName, deviceAddress, sessionId);
                }
            }
            catch (IOException e) { output = null; runOnUiThread(() -> { stopMotion(); status.setText("Bluetooth desconectado"); }); }
        });
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == REQUEST_BT && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) refreshDevices();
        else status.setText("Se necesita permiso Bluetooth para conectar");
    }

    @Override protected void onPause() {
        stopMotion();
        super.onPause();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) stopMotion();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        heldCommand = null;
        holdHandler.removeCallbacks(heartbeat);
        sender.shutdown();
        OutputStream stream = output;
        if (stream != null) try { stream.write("S,0\n".getBytes()); stream.flush(); } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        super.onDestroy();
    }
}
