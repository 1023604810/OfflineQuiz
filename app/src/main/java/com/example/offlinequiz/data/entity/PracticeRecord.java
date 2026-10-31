package com.example.offlinequiz.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;

/** One answer attempt. {@code isCorrect}: 1 correct, 0 wrong, 2 partial. */
@Entity(tableName = "practice_records",
        foreignKeys = @ForeignKey(entity = Question.class,
                parentColumns = "id", childColumns = "question_id",
                onDelete = CASCADE),
        indices = {@Index("question_id"), @Index("created_at")})
public class PracticeRecord {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "question_id")
    public long questionId;

    @ColumnInfo(name = "user_answer")
    public String userAnswer;

    @ColumnInfo(name = "is_correct")
    public int isCorrect;

    /** Seconds spent on this question. */
    @ColumnInfo(name = "time_cost")
    public int timeCost;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    public PracticeRecord() {
    }

    @androidx.room.Ignore
    public PracticeRecord(@NonNull long questionId, String userAnswer, int isCorrect,
                          int timeCost, long createdAt) {
        this.questionId = questionId;
        this.userAnswer = userAnswer;
        this.isCorrect = isCorrect;
        this.timeCost = timeCost;
        this.createdAt = createdAt;
    }
}
