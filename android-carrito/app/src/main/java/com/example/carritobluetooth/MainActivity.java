package com.example.carritobluetooth;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
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
    private TextView status, speedLabel;
    private SeekBar speedBar;
    private final ArrayList<BluetoothDevice> bonded = new ArrayList<>();
    private volatile boolean connecting;
    private final Handler holdHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService sender = Executors.newSingleThreadExecutor();
    private final MovementTracker movementTracker = new MovementTracker();
    private MovementHistory history;
    private volatile String connectionSession = "", connectedName = "Carrito", connectedAddress = "";
    private String heldCommand;
    private int heldPointerId = -1;
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
        heldPointerId = -1;
        holdHandler.removeCallbacks(heartbeat);
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

    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1, -2); }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24, 28, 24, 24); root.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = new TextView(this); title.setText("Carrito Bluetooth"); title.setTextSize(26); title.setGravity(Gravity.CENTER); root.addView(title, full());
        status = new TextView(this); status.setText("Desconectado"); status.setTextSize(16); status.setGravity(Gravity.CENTER); root.addView(status, full());
        devices = new Spinner(this); root.addView(devices, full());
        Button refresh = new Button(this); refresh.setText("Actualizar dispositivos emparejados"); refresh.setOnClickListener(v -> refreshDevices()); root.addView(refresh, full());
        Button connect = new Button(this); connect.setText("Conectar"); connect.setOnClickListener(v -> connectSelected()); root.addView(connect, full());
        speedLabel = new TextView(this); speedLabel.setText("Velocidad: 55%"); speedLabel.setTextSize(18); root.addView(speedLabel, full());
        speedBar = new SeekBar(this); speedBar.setMax(100); speedBar.setProgress(55); root.addView(speedBar, full());
        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean user) { speedLabel.setText("Velocidad: " + p + "%"); }
            public void onStartTrackingTouch(SeekBar b) { stopMotion(); }
            public void onStopTrackingTouch(SeekBar b) { stopMotion(); }
        });
        addControl(root, "▲  Adelante", "F");
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER);
        addControl(row, "◀ Izquierda", "R"); addControl(row, "■ Detener", "S"); addControl(row, "Derecha ▶", "L"); root.addView(row, full());
        addControl(root, "▼  Atrás", "B");
        Button historyButton = new Button(this); historyButton.setText("Historial de movimientos");
        historyButton.setOnClickListener(v -> {
            stopMotion();
            startActivity(new Intent(this, HistoryActivity.class));
        });
        root.addView(historyButton, full());
        if (Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            view.setPadding(24 + bars.left, 28 + bars.top, 24 + bars.right, 24 + bars.bottom);
            return insets;
        });
        setContentView(root);
    }

    private void addControl(LinearLayout parent, String label, String command) {
        Button b = new Button(this); b.setText(label); b.setTextSize(16); b.setMinHeight(68); bindHold(b, command);
        parent.addView(b, parent.getOrientation() == LinearLayout.HORIZONTAL ? new LinearLayout.LayoutParams(0, -2, 1) : full());
    }

    private void bindHold(Button b, String command) {
        b.setOnTouchListener((v, e) -> {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                if (command.equals("S")) { stopMotion(); return true; }
                if (heldCommand != null) return true;
                heldCommand = command;
                heldPointerId = e.getPointerId(e.getActionIndex());
                holdHandler.post(heartbeat);
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                if (command.equals(heldCommand) && e.getPointerId(e.getActionIndex()) == heldPointerId) stopMotion();
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                if (command.equals(heldCommand)) stopMotion();
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE && command.equals(heldCommand)) {
                int index = e.findPointerIndex(heldPointerId);
                if (index < 0 || e.getX(index) < 0 || e.getX(index) >= v.getWidth()
                        || e.getY(index) < 0 || e.getY(index) >= v.getHeight()) stopMotion();
            }
            return true;
        });
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
        devices.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
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
                connectedName = device.getName() == null ? "Carrito" : device.getName();
                connectedAddress = device.getAddress();
                connectionSession = UUID.randomUUID().toString();
                socket = candidate; output = candidate.getOutputStream();
                runOnUiThread(() -> status.setText("Conectado a " + device.getName()));
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
        heldCommand = null;
        holdHandler.removeCallbacks(heartbeat);
        sender.shutdown();
        OutputStream stream = output;
        if (stream != null) try { stream.write("S,0\n".getBytes()); stream.flush(); } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        super.onDestroy();
    }
}
