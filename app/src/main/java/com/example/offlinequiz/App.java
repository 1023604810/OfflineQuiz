package com.example.offlinequiz;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import com.example.offlinequiz.data.AppDatabase;
import com.example.offlinequiz.util.Prefs;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Application entry point: owns the single Room database instance, a small IO pool for
 * blocking DB/parse work and the main-thread handler used to publish results.
 */
public class App extends Application {

    private static App instance;
    private AppDatabase database;
    private Prefs prefs;

    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        database = AppDatabase.getInstance(this);
        prefs = new Prefs(this);
        io.execute(() -> {
            try {
                // v2 -> v3 migration leaves stem_key NULL; fill it before anything upserts
                new com.example.offlinequiz.service.StemKeyBackfill(database).backfill();
                // one-time removal of figure-referencing questions without an actual figure
                new com.example.offlinequiz.service.FigurelessQuestionCleaner(this, database)
                        .cleanIfNeeded();
                new com.example.offlinequiz.service.DifficultyCalibrator(database).calibrate(false);
            } catch (Exception ignored) {
                // best-effort maintenance; the bank stays usable with its current values
            }
        });
    }

    public static App get() {
        return instance;
    }

    public AppDatabase db() {
        return database;
    }

    public Prefs prefs() {
        return prefs;
    }

    public ExecutorService io() {
        return io;
    }

    public Handler main() {
        return main;
    }

    public void runOnUiThread(Runnable r) {
        main.post(r);
    }
}
