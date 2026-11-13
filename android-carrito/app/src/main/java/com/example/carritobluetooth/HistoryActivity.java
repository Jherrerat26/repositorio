package com.example.carritobluetooth;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
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
        TextView view = NeonTheme.text(this, text, size, color);
        view.setPadding(0, dp(4), 0, dp(4));
        return view;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NeonTheme.BACKGROUND);
        LinearLayout actions = new LinearLayout(this);
        Button back = NeonTheme.button(this, "‹  Volver al control", false);
        back.setOnClickListener(v -> finish());
        Button sync = NeonTheme.button(this, "Sincronizar", true);
        sync.setOnClickListener(v -> { history.sync(); reload(); });
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        backParams.rightMargin = dp(10);
        actions.addView(back, backParams);
        actions.addView(sync, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams actionParams = full(); actionParams.bottomMargin = dp(12);
        root.addView(actions, actionParams);
        root.addView(label("Historial", 26, NeonTheme.TEXT), full());
        TextView caption = label("MOVIMIENTOS · FIREBASE", 10, NeonTheme.CYAN);
        caption.setLetterSpacing(.15f);
        root.addView(caption, full());
        syncStatus = label(history.getSyncStatus(), 14, NeonTheme.MUTED);
        syncStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(syncStatus, full());
        summary = label("Cargando registros…", 13, NeonTheme.MUTED); root.addView(summary, full());
        root.addView(label("FILTRAR DIRECCIÓN", 11, NeonTheme.CYAN), full());
        Spinner filters = new Spinner(this);
        filters.setContentDescription("Filtrar movimientos por dirección");
        filters.setBackground(NeonTheme.panel(this, NeonTheme.PANEL, NeonTheme.BORDER, 12));
        filters.setAdapter(NeonTheme.spinnerAdapter(this, FILTERS));
        filters.setSelection(filter);
        filters.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (filter != position) { filter = position; limit = 200; reload(); }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        LinearLayout.LayoutParams filterParams = full(); filterParams.bottomMargin = dp(12);
        root.addView(filters, filterParams);

        FrameLayout content = new FrameLayout(this);
        ListView list = new ListView(this);
        list.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(dp(8));
        list.setSelector(android.R.color.transparent);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(8));
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
                    card.setBackground(NeonTheme.panel(HistoryActivity.this, NeonTheme.PANEL, NeonTheme.BORDER, 16));
                    card.setPadding(dp(14), dp(10), dp(14), dp(10));
                    card.addView(label("", 18, NeonTheme.CYAN), full());
                    card.addView(label("", 14, NeonTheme.TEXT), full());
                    card.addView(label("", 14, NeonTheme.MUTED), full());
                    card.addView(label("", 13, NeonTheme.MUTED), full());
                }
                MovementHistory.Entry entry = rows.get(position);
                ((TextView) card.getChildAt(0)).setText(MovementTracker.direction(entry.command));
                ((TextView) card.getChildAt(1)).setText(date.format(new java.util.Date(entry.timestamp)));
                ((TextView) card.getChildAt(2)).setText("Velocidad: " + entry.speed + "/255 ("
                        + Math.round(entry.speed * 100f / 255f) + "%) · " + entry.deviceName);
                TextView state = (TextView) card.getChildAt(3);
                state.setText(entry.synced ? "✓ Sincronizado con Firebase" : "◷ Pendiente de sincronizar");
                state.setTextColor(entry.synced ? NeonTheme.CYAN : NeonTheme.MUTED);
                return card;
            }
        };
        list.setAdapter(adapter);
        content.addView(list, new FrameLayout.LayoutParams(-1, -1));
        TextView empty = label("Sin movimientos todavía\n\nConecta el carrito y utiliza el joystick.\nTus registros aparecerán aquí.", 16, NeonTheme.MUTED);
        empty.setGravity(Gravity.CENTER);
        content.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        list.setEmptyView(empty);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        more = NeonTheme.button(this, "Mostrar más registros", false); more.setEnabled(false);
        more.setOnClickListener(v -> { limit = Math.min(limit + 200, 10000); reload(); });
        root.addView(more, full());

        NeonTheme.window(this, root);
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
