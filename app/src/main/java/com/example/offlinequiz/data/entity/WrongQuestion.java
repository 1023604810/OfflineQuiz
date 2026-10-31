package com.example.offlinequiz.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;

@Entity(tableName = "wrong_questions",
        foreignKeys = @ForeignKey(entity = Question.class,
                parentColumns = "id", childColumns = "question_id",
                onDelete = CASCADE),
        indices = {@Index(value = "question_id", unique = true)})
public class WrongQuestion {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "question_id")
    public long questionId;

    @ColumnInfo(name = "wrong_count")
    public int wrongCount = 1;

    @ColumnInfo(name = "correct_count")
    public int correctCount;

    @ColumnInfo(name = "last_wrong_at")
    public long lastWrongAt;

    /** 0 = still in the wrong book, 1 = resolved (moved out). */
    public int resolved;
}
