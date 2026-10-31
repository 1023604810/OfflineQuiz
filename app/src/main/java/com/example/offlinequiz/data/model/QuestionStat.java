package com.example.offlinequiz.data.model;

/** Aggregated answer statistics for one question (used by the difficulty calibrator). */
public class QuestionStat {
    public long questionId;
    public int attempts;
    public int wrong;
}
