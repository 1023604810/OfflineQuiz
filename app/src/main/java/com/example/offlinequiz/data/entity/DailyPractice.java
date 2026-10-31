package com.example.offlinequiz.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Today's generated daily-practice set, keyed by yyyy-MM-dd. */
@Entity(tableName = "daily_practice",
        indices = {@Index(value = "date_key", unique = true)})
public class DailyPractice {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "date_key")
    public String dateKey;

    /** JSON array of question ids, in answering order. */
    @ColumnInfo(name = "question_ids")
    @NonNull
    public String questionIds = "[]";

    @ColumnInfo(name = "total_count")
    public int totalCount;

    @ColumnInfo(name = "done_count")
    public int doneCount;

    @ColumnInfo(name = "correct_count")
    public int correctCount;
}
