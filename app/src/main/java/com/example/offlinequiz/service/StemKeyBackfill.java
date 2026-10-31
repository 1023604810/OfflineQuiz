package com.example.offlinequiz.service;

import com.example.offlinequiz.data.AppDatabase;
import com.example.offlinequiz.data.entity.Question;
import com.example.offlinequiz.util.QuestionKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Back-fills {@code stem_key} for rows created before schema v3 (they are NULL after
 * the migration). Newest duplicates are kept: if two existing rows normalise to the
 * same stem, the older one is removed so the unique index and "only the latest version"
 * rule both hold.
 */
public class StemKeyBackfill {

    private final AppDatabase db;

    public StemKeyBackfill(AppDatabase db) {
        this.db = db;
    }

    /** @return number of rows that received a key (0 when nothing needed back-filling). */
    public int backfill() {
        List<Question> missing = new ArrayList<>(db.questionDao().withoutStemKey());
        if (missing.isEmpty()) {
            return 0;
        }
        missing.sort(Comparator.comparingLong((Question q) -> q.createdAt).reversed()
                .thenComparing(Comparator.comparingLong((Question q) -> q.id).reversed()));
        return db.runInTransaction(() -> {
            int filled = 0;
            for (Question q : missing) {
                String key = QuestionKey.of(q.stem);
                if (key.isEmpty()) {
                    continue;
                }
                Question clash = db.questionDao().findByStemKey(key);
                if (clash != null) {
                    db.questionDao().deleteById(q.id);
                } else {
                    db.questionDao().updateStemKey(q.id, key);
                    filled++;
                }
            }
            return filled;
        });
    }
}
