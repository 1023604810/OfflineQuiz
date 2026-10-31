package com.example.offlinequiz.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Result of submitting a mock paper. */
public class ExamResult {

    public double userScore;
    public double totalScore;
    public int totalQuestions;
    public int correctCount;
    public int partialCount;
    public int wrongCount;
    public int unanswered;
    public String shortageNote;

    /** type -> [total, correct, score] */
    public final Map<String, double[]> byType = new LinkedHashMap<>();

    /** Knowledge tags that scored below 50%, with their question ids. */
    public final List<String> weakKnowledge = new ArrayList<>();

    public int accuracy() {
        int graded = correctCount + partialCount + wrongCount;
        return graded == 0 ? 0 : Math.round(correctCount * 100f / graded);
    }
}
