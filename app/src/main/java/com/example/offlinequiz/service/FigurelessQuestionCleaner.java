package com.example.offlinequiz.service;

import android.content.Context;

import com.example.offlinequiz.data.AppDatabase;
import com.example.offlinequiz.data.entity.AppConfig;
import com.example.offlinequiz.data.entity.Question;
import com.example.offlinequiz.util.QuestionKey;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One-time removal of questions that reference a figure ("如图所示" ...) although the
 * source document never contained one.
 *
 * <p>The removal list ships in {@code assets/questionbank/removed_figureless.json}
 * (id + stem + the source-file media check), so the decision stays auditable and the
 * app removes exactly those rows - never a heuristic that could hit a question that
 * really has a diagram. Runs once at app start (marker in {@code app_config}) and again
 * after any import, so importing an old bank cannot bring them back.</p>
 */
public class FigurelessQuestionCleaner {

    public static final String CONFIG_KEY = "figureless_cleanup";
    public static final int VERSION = 1;
    private static final String REMOVAL_ASSET = "questionbank/removed_figureless.json";

    private final Context context;
    private final AppDatabase db;

    public FigurelessQuestionCleaner(Context context, AppDatabase db) {
        this.context = context.getApplicationContext();
        this.db = db;
    }

    /** Runs only once per install/version. */
    public int cleanIfNeeded() {
        String marker = db.configDao().get(CONFIG_KEY);
        if (marker != null && marker.startsWith(VERSION + ":")) {
            return 0;
        }
        return clean();
    }

    /** Removes matching rows regardless of the marker and refreshes it. */
    public int clean() {
        Set<String> keys = loadRemovedKeys();
        if (keys.isEmpty()) {
            return 0;
        }
        List<Question> questions = db.questionDao().getAllOrdered();
        final int[] deleted = {0};
        db.runInTransaction(() -> {
            for (Question q : questions) {
                if (keys.contains(QuestionKey.of(q.stem))) {
                    db.questionDao().deleteById(q.id);
                    deleted[0]++;
                }
            }
        });
        db.configDao().put(new AppConfig(CONFIG_KEY, VERSION + ":" + deleted[0]));
        return deleted[0];
    }

    private Set<String> loadRemovedKeys() {
        Set<String> keys = new LinkedHashSet<>();
        try (InputStream in = context.getAssets().open(REMOVAL_ASSET);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray array = root.getAsJsonArray("questions");
            for (JsonElement element : array) {
                JsonObject item = element.getAsJsonObject();
                JsonElement stem = item.get("stem");
                if (stem != null) {
                    keys.add(QuestionKey.of(stem.getAsString()));
                }
            }
        } catch (Exception ignored) {
            // no list / malformed asset: treat as nothing to remove
        }
        return keys;
    }
}
