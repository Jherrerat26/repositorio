package com.example.carritobluetooth;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Durable local outbox. Disk and network work never run on the Bluetooth sender. */
public final class MovementHistory {
    private static MovementHistory instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService disk = Executors.newSingleThreadExecutor();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Store store;
    private FirebaseAuth auth;
    private FirebaseDatabase database;
    private DatabaseReference movements;
    private String status = "Preparando historial…";
    private boolean authenticating, syncing, retryScheduled;

    public static synchronized MovementHistory get(Context context) {
        if (instance == null) instance = new MovementHistory(context.getApplicationContext());
        return instance;
    }

    private MovementHistory(Context context) {
        store = new Store(context);
        try {
            FirebaseApp app = FirebaseApp.initializeApp(context);
            if (app == null || app.getOptions().getDatabaseUrl() == null) {
                status = "Historial local · Firebase pendiente de configuración";
                return;
            }
            auth = FirebaseAuth.getInstance(app);
            database = FirebaseDatabase.getInstance(app);
            database.getReference(".info/connected").addValueEventListener(new ValueEventListener() {
                @Override public void onDataChange(DataSnapshot snapshot) {
                    if (Boolean.TRUE.equals(snapshot.getValue(Boolean.class))) sync();
                    else setStatus("Sin conexión · los movimientos se guardan en este teléfono");
                }
                @Override public void onCancelled(DatabaseError error) {
                    setStatus("No se pudo comprobar la conexión con Firebase");
                }
            });
            sync();
        } catch (RuntimeException error) {
            auth = null;
            database = null;
            status = "Historial local · revisa la configuración de Firebase";
        }
    }

    public interface Listener {
        void onLoaded(List<Entry> rows, int pending, int total);
    }

    public static final class Entry {
        public final String id, command, deviceName, deviceAddress, sessionId;
        public final int speed;
        public final long timestamp;
        public final boolean synced;

        private Entry(String id, String command, int speed, long timestamp,
                String deviceName, String deviceAddress, String sessionId, boolean synced) {
            this.id = id;
            this.command = command;
            this.speed = speed;
            this.timestamp = timestamp;
            this.deviceName = deviceName;
            this.deviceAddress = deviceAddress;
            this.sessionId = sessionId;
            this.synced = synced;
        }

        private Map<String, Object> cloudValues() {
            Map<String, Object> values = new HashMap<>();
            values.put("command", command);
            values.put("direction", MovementTracker.direction(command));
            values.put("speed", speed);
            values.put("timestamp", timestamp);
            values.put("deviceName", deviceName);
            values.put("deviceAddress", deviceAddress);
            values.put("sessionId", sessionId);
            return values;
        }

        private ContentValues localValues() {
            ContentValues values = new ContentValues();
            values.put("id", id);
            values.put("command", command);
            values.put("speed", speed);
            values.put("timestamp", timestamp);
            values.put("device_name", deviceName);
            values.put("device_address", deviceAddress);
            values.put("session_id", sessionId);
            values.put("synced", synced ? 1 : 0);
            return values;
        }

        private static Entry read(Cursor cursor) {
            return new Entry(cursor.getString(0), cursor.getString(1), cursor.getInt(2),
                    cursor.getLong(3), cursor.getString(4), cursor.getString(5),
                    cursor.getString(6), cursor.getInt(7) != 0);
        }

        private static Entry remote(DataSnapshot snapshot) {
            String command = snapshot.child("command").getValue(String.class);
            Long speed = snapshot.child("speed").getValue(Long.class);
            Long timestamp = snapshot.child("timestamp").getValue(Long.class);
            String name = snapshot.child("deviceName").getValue(String.class);
            String address = snapshot.child("deviceAddress").getValue(String.class);
            String session = snapshot.child("sessionId").getValue(String.class);
            if (command == null || !command.matches("[FBLRS]") || speed == null
                    || speed < 0 || speed > 255 || timestamp == null || timestamp <= 0
                    || name == null || address == null || session == null) return null;
            return new Entry(snapshot.getKey(), command, speed.intValue(), timestamp,
                    name, address, session, true);
        }
    }

    public void addListener(Runnable listener) { listeners.addIfAbsent(listener); }
    public void removeListener(Runnable listener) { listeners.remove(listener); }
    public String getSyncStatus() { return status; }

    private void changed() {
        for (Runnable listener : listeners) listener.run();
    }

    private void setStatus(String value) {
        if (value.equals(status)) return;
        status = value;
        changed();
    }

    public void record(String command, int speed, long timestamp, String deviceName,
            String deviceAddress, String sessionId) {
        Entry entry = new Entry(UUID.randomUUID().toString(), command, speed, timestamp,
                deviceName, deviceAddress, sessionId, false);
        disk.execute(() -> {
            try {
                store.getWritableDatabase().insertOrThrow("movements", null, entry.localValues());
                main.post(() -> { changed(); sync(); });
            } catch (RuntimeException error) {
                main.post(() -> setStatus("No se pudo guardar el movimiento en este teléfono"));
            }
        });
    }

    public void load(String command, int limit, Listener listener) {
        disk.execute(() -> {
            List<Entry> entries = new ArrayList<>();
            int pending = 0, total = 0;
            try {
                SQLiteDatabase db = store.getReadableDatabase();
                try (Cursor count = db.rawQuery("SELECT COUNT(*), COALESCE(SUM(CASE WHEN synced=0 THEN 1 ELSE 0 END),0) FROM movements", null)) {
                    if (count.moveToFirst()) { total = count.getInt(0); pending = count.getInt(1); }
                }
                try (Cursor cursor = db.query("movements", null,
                        command == null ? null : "command=?", command == null ? null : new String[]{command},
                        null, null, "timestamp DESC, rowid DESC", String.valueOf(Math.max(1, Math.min(limit, 10000))))) {
                    while (cursor.moveToNext()) entries.add(Entry.read(cursor));
                }
            } catch (RuntimeException error) {
                main.post(() -> setStatus("No se pudo leer el historial local"));
            }
            final int pendingCount = pending, totalCount = total;
            main.post(() -> listener.onLoaded(entries, pendingCount, totalCount));
        });
    }

    /** Must be called on the main thread. Retrying uses the same event IDs. */
    public void sync() {
        if (auth == null || database == null || syncing || authenticating) return;
        if (auth.getCurrentUser() == null) {
            authenticating = true;
            setStatus("Conectando el historial con Firebase…");
            auth.signInAnonymously().addOnCompleteListener(task -> {
                authenticating = false;
                if (task.isSuccessful()) sync();
                else {
                    setStatus("Firebase no disponible · guardado local; vuelve a sincronizar");
                    retryLater();
                }
            });
            return;
        }
        if (movements == null) {
            movements = database.getReference("users").child(auth.getCurrentUser().getUid()).child("movements");
            movements.orderByChild("timestamp").limitToLast(200).addValueEventListener(new ValueEventListener() {
                @Override public void onDataChange(DataSnapshot snapshot) {
                    disk.execute(() -> {
                        try {
                            SQLiteDatabase db = store.getWritableDatabase();
                            boolean inserted = false;
                            for (DataSnapshot item : snapshot.getChildren()) {
                                Entry entry = Entry.remote(item);
                                // Ignore existing rows: local events only become synced after server acknowledgement.
                                if (entry != null && db.insertWithOnConflict("movements", null,
                                        entry.localValues(), SQLiteDatabase.CONFLICT_IGNORE) != -1) inserted = true;
                            }
                            if (inserted) main.post(() -> changed());
                        } catch (RuntimeException error) {
                            main.post(() -> setStatus("No se pudo recuperar el historial de Firebase"));
                        }
                    });
                }
                @Override public void onCancelled(DatabaseError error) {
                    setStatus("Sin acceso al historial de Firebase · revisa las reglas de la base de datos");
                }
            });
        }
        syncing = true;
        disk.execute(() -> {
            Entry next = null;
            try (Cursor cursor = store.getReadableDatabase().query("movements", null, "synced=0",
                    null, null, null, "timestamp ASC, rowid ASC", "1")) {
                if (cursor.moveToFirst()) next = Entry.read(cursor);
            } catch (RuntimeException error) {
                main.post(() -> { syncing = false; setStatus("No se pudo leer la cola de sincronización"); });
                return;
            }
            final Entry entry = next;
            main.post(() -> {
                if (entry == null) {
                    syncing = false;
                    setStatus("Historial sincronizado con Firebase");
                    return;
                }
                setStatus("Sincronizando movimientos con Firebase…");
                movements.child(entry.id).setValue(entry.cloudValues()).addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        syncing = false;
                        setStatus("No se pudo sincronizar · los registros siguen guardados en el teléfono");
                        retryLater();
                        return;
                    }
                    disk.execute(() -> {
                        try {
                            ContentValues values = new ContentValues(); values.put("synced", 1);
                            store.getWritableDatabase().update("movements", values, "id=?", new String[]{entry.id});
                            main.post(() -> { syncing = false; changed(); sync(); });
                        } catch (RuntimeException error) {
                            main.post(() -> {
                                syncing = false;
                                setStatus("Guardado en Firebase · confirmación local pendiente");
                                retryLater();
                            });
                        }
                    });
                });
            });
        });
    }

    private void retryLater() {
        if (retryScheduled) return;
        retryScheduled = true;
        main.postDelayed(() -> { retryScheduled = false; sync(); }, 30000);
    }

    private static final class Store extends SQLiteOpenHelper {
        Store(Context context) { super(context, "movement_history.db", null, 1); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE movements (id TEXT PRIMARY KEY NOT NULL, command TEXT NOT NULL, speed INTEGER NOT NULL, timestamp INTEGER NOT NULL, device_name TEXT NOT NULL, device_address TEXT NOT NULL, session_id TEXT NOT NULL, synced INTEGER NOT NULL DEFAULT 0)");
            db.execSQL("CREATE INDEX movements_timestamp ON movements(timestamp DESC)");
            db.execSQL("CREATE INDEX movements_pending ON movements(synced,timestamp)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Schema v1. Future migrations must preserve movement history.
        }
    }
}
