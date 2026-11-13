package com.example.carritobluetooth;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends Activity {
    private static final String[] FILTERS = {"Todos", "Adelante", "Atrás", "Izquierda", "Derecha", "Detener"};
    private static final String[] COMMANDS = {null, "F", "B", "R", "L", "S"};
    private final List<MovementHistory.Entry> rows = new ArrayList<>();
    private final SimpleDateFormat date = new SimpleDateFormat("dd/MM/yyyy · HH:mm:ss", new Locale("es", "CO"));
    private final Runnable changed = this::reload;
    private MovementHistory history;
    private TextView syncStatus, summary;
    private Button more;
    private BaseAdapter adapter;
    private int filter, limit = 200, request;
    private boolean started;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        history = MovementHistory.get(this);
        if (state != null) {
            filter = Math.max(0, Math.min(state.getInt("filter", 0), FILTERS.length - 1));
            limit = Math.max(200, Math.min(state.getInt("limit", 200), 10000));
        }
        buildUi();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1, -2); }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(4), 0, dp(4));
        return view;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 248, 252));
        int padding = dp(18);
        root.setPadding(padding, dp(12), padding, dp(12));
        LinearLayout actions = new LinearLayout(this);
        Button back = new Button(this); back.setText("Volver"); back.setOnClickListener(v -> finish());
        Button sync = new Button(this); sync.setText("Sincronizar");
        sync.setOnClickListener(v -> { history.sync(); reload(); });
        actions.addView(back, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(sync, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(actions, full());
        root.addView(label("Historial de movimientos", 25, Color.rgb(24, 44, 74)), full());
        syncStatus = label(history.getSyncStatus(), 14, Color.rgb(74, 88, 108));
        syncStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(syncStatus, full());
        summary = label("Cargando registros…", 13, Color.DKGRAY); root.addView(summary, full());
        root.addView(label("Filtrar movimiento", 14, Color.DKGRAY), full());
        Spinner filters = new Spinner(this);
        filters.setContentDescription("Filtrar movimientos por dirección");
        filters.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, FILTERS));
        filters.setSelection(filter);
        filters.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (filter != position) { filter = position; limit = 200; reload(); }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        root.addView(filters, full());

        FrameLayout content = new FrameLayout(this);
        ListView list = new ListView(this);
        list.setDividerHeight(dp(8));
        adapter = new BaseAdapter() {
            @Override public int getCount() { return rows.size(); }
            @Override public Object getItem(int position) { return rows.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                LinearLayout card;
                if (recycled instanceof LinearLayout) card = (LinearLayout) recycled;
                else {
                    card = new LinearLayout(HistoryActivity.this);
                    card.setOrientation(LinearLayout.VERTICAL);
                    card.setBackgroundColor(Color.WHITE);
                    card.setPadding(dp(14), dp(10), dp(14), dp(10));
                    card.addView(label("", 18, Color.rgb(24, 44, 74)), full());
                    card.addView(label("", 14, Color.DKGRAY), full());
                    card.addView(label("", 14, Color.DKGRAY), full());
                    card.addView(label("", 13, Color.DKGRAY), full());
                }
                MovementHistory.Entry entry = rows.get(position);
                ((TextView) card.getChildAt(0)).setText(MovementTracker.direction(entry.command));
                ((TextView) card.getChildAt(1)).setText(date.format(new java.util.Date(entry.timestamp)));
                ((TextView) card.getChildAt(2)).setText("Velocidad: " + entry.speed + "/255 ("
                        + Math.round(entry.speed * 100f / 255f) + "%) · " + entry.deviceName);
                TextView state = (TextView) card.getChildAt(3);
                state.setText(entry.synced ? "✓ Sincronizado con Firebase" : "◷ Pendiente de sincronizar");
                state.setTextColor(entry.synced ? Color.rgb(30, 110, 75) : Color.rgb(135, 80, 20));
                return card;
            }
        };
        list.setAdapter(adapter);
        content.addView(list, new FrameLayout.LayoutParams(-1, -1));
        TextView empty = label("No hay movimientos para mostrar.\nConecta el carrito y utiliza los controles.", 16, Color.DKGRAY);
        empty.setGravity(Gravity.CENTER);
        content.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        list.setEmptyView(empty);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        more = new Button(this); more.setText("Mostrar más registros"); more.setEnabled(false);
        more.setOnClickListener(v -> { limit = Math.min(limit + 200, 10000); reload(); });
        root.addView(more, full());

        if (Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            view.setPadding(padding + bars.left, dp(12) + bars.top, padding + bars.right, dp(12) + bars.bottom);
            return insets;
        });
        setContentView(root);
    }

    private void reload() {
        if (!started) return;
        syncStatus.setText(history.getSyncStatus());
        int currentRequest = ++request;
        history.load(COMMANDS[filter], limit, (entries, pending, total) -> {
            if (!started || isFinishing() || isDestroyed() || request != currentRequest) return;
            rows.clear(); rows.addAll(entries); adapter.notifyDataSetChanged();
            syncStatus.setText(history.getSyncStatus());
            summary.setText(entries.size() + " visibles · " + total + " guardados · " + pending + " pendientes");
            more.setEnabled(entries.size() >= limit && limit < 10000);
            more.setVisibility(entries.size() >= limit ? View.VISIBLE : View.GONE);
        });
    }

    @Override protected void onStart() {
        super.onStart(); started = true; history.addListener(changed); reload();
    }

    @Override protected void onStop() {
        started = false; request++; history.removeListener(changed); super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("filter", filter); state.putInt("limit", limit); super.onSaveInstanceState(state);
    }
}
