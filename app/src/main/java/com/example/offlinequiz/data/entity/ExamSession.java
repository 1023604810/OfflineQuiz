package com.example.offlinequiz.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A generated mock paper plus its progress and result. */
@Entity(tableName = "exam_sessions")
public class ExamSession {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String title;

    @ColumnInfo(name = "question_ids")
    @NonNull
    public String questionIds = "[]";

    @ColumnInfo(name = "total_score")
    public double totalScore;

    /** Minutes. */
    @ColumnInfo(name = "time_limit")
    public int timeLimit;

    @ColumnInfo(name = "start_time")
    public long startTime;

    @ColumnInfo(name = "submit_time")
    public long submitTime;

    @ColumnInfo(name = "user_score")
    public double userScore;

    /** 0 = in progress, 1 = submitted. */
    public int status;

    /** JSON object: {"questionId":"userAnswer"} - lets an unfinished paper resume. */
    @ColumnInfo(name = "answers")
    public String answers = "{}";

    /** Set when one or more type buckets could not be filled to the requested count. */
    @ColumnInfo(name = "shortage_note")
    public String shortageNote;
}
