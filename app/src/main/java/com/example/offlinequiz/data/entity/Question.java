package com.example.offlinequiz.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A question row. {@code options} stores the JSON array string
 * (["A. xxx", "B. yyy", ...]) exactly as documented in the import format.
 */
@Entity(tableName = "questions",
        indices = {@Index("type"), @Index("knowledge"), @Index("difficulty"),
                @Index(value = "stem_key", unique = true)})
public class Question {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** External id supplied by the bank file, may be null. */
    @ColumnInfo(name = "ext_id")
    public String extId;

    @NonNull
    public String stem = "";

    /** Normalised stem used for "same question" detection (NFKC, no spaces, lower case). */
    @ColumnInfo(name = "stem_key")
    public String stemKey;

    /** single / multiple / judge / blank / essay */
    @NonNull
    public String type = "single";

    /** JSON array of option strings. */
    public String options;

    @NonNull
    public String answer = "";

    public String analysis;

    public String knowledge;

    /** 1 = easy, 2 = medium, 3 = hard (computed by DifficultyCalibrator). */
    public int difficulty = 2;

    /** 0-100 difficulty score computed from knowledge coverage + error rate + structure. */
    @ColumnInfo(name = "difficulty_score")
    public int difficultyScore;

    /** Human readable explanation of the difficulty score. */
    @ColumnInfo(name = "difficulty_reason")
    public String difficultyReason;

    /** MD5(stem + options), used for de-duplication on import. */
    @ColumnInfo(name = "content_hash")
    @NonNull
    public String contentHash = "";

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
