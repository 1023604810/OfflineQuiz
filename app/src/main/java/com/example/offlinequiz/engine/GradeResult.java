package com.example.offlinequiz.engine;

/** Result of grading one answer. */
public class GradeResult {

    public enum Grade {CORRECT, WRONG, PARTIAL, SELF_EVAL}

    public final Grade grade;
    public final String correctAnswer;
    public final String analysis;

    public GradeResult(Grade grade, String correctAnswer, String analysis) {
        this.grade = grade;
        this.correctAnswer = correctAnswer;
        this.analysis = analysis;
    }

    public boolean isCorrect() {
        return grade == Grade.CORRECT;
    }

    /** DB representation: 1 correct, 0 wrong, 2 partial, 3 self-eval (treated as correct for stats). */
    public int toDbFlag() {
        switch (grade) {
            case CORRECT:
            case SELF_EVAL:
                return 1;
            case PARTIAL:
                return 2;
            default:
                return 0;
        }
    }
}
