package com.example.offlinequiz.service;

import com.example.offlinequiz.data.AppDatabase;
import com.example.offlinequiz.data.entity.Question;
import com.example.offlinequiz.util.QuestionKey;

/**
 * Insert-or-overwrite for one question, keyed by the normalised stem.
 *
 * <p>Semantics required by "新增题目":</p>
 * <ol>
 *   <li>compute the exact-match key (NFKC, whitespace removed, lower case);</li>
 *   <li>same key already in the bank -> the existing row is overwritten in place
 *       (same id, so practice records / wrong book / favorites keep pointing at it),
 *       reported as {@link Outcome#UPDATED};</li>
 *   <li>otherwise insert a new row, reported as {@link Outcome#INSERTED}.</li>
 * </ol>
 *
 * <p>The lookup + write happen inside one Room transaction, and the unique index on
 * {@code stem_key} is the final guard against duplicate rows.</p>
 */
public class QuestionUpsertService {

    public enum Outcome {INSERTED, UPDATED}

    public static class Result {
        public final Outcome outcome;
        public final long questionId;

        public Result(Outcome outcome, long questionId) {
            this.outcome = outcome;
            this.questionId = questionId;
        }

        public boolean isUpdated() {
            return outcome == Outcome.UPDATED;
        }
    }

    private final AppDatabase db;

    public QuestionUpsertService(AppDatabase db) {
        this.db = db;
    }

    public Result upsert(Question candidate) {
        String key = QuestionKey.of(candidate.stem);
        candidate.stemKey = key;
        return db.runInTransaction(() -> {
            Question existing = db.questionDao().findByStemKey(key);
            if (existing != null) {
                overwrite(existing, candidate);
                db.questionDao().update(existing);
                return new Result(Outcome.UPDATED, existing.id);
            }
            candidate.id = 0;
            long id = db.questionDao().insert(candidate);
            if (id == -1) {
                // unique index raced with another writer: fall back to update
                Question raced = db.questionDao().findByStemKey(key);
                if (raced != null) {
                    overwrite(raced, candidate);
                    db.questionDao().update(raced);
                    return new Result(Outcome.UPDATED, raced.id);
                }
            }
            return new Result(Outcome.INSERTED, id);
        });
    }

    /** Copies every content field from {@code source} onto {@code target}, keeping its id. */
    private static void overwrite(Question target, Question source) {
        target.extId = source.extId;
        target.stem = source.stem;
        target.stemKey = source.stemKey;
        target.type = source.type;
        target.options = source.options;
        target.answer = source.answer;
        target.analysis = source.analysis;
        target.knowledge = source.knowledge;
        target.difficulty = source.difficulty;
        target.difficultyScore = 0;
        target.difficultyReason = null;
        target.contentHash = source.contentHash;
        target.createdAt = System.currentTimeMillis();
    }
}
