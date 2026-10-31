package com.example.offlinequiz.engine;

import com.example.offlinequiz.data.entity.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Grading rules, one branch per question type:
 * single - exact match; multiple - full/partial/wrong; judge - T/F;
 * blank - "|" separated blanks, all must match (case insensitive);
 * essay - self evaluation.
 */
public final class AnswerEngine {

    private AnswerEngine() {
    }

    public static GradeResult grade(Question q, String userAnswer) {
        String type = q.type == null ? "single" : q.type;
        switch (type) {
            case "single":
                return gradeSingle(q, userAnswer);
            case "multiple":
                return gradeMultiple(q, userAnswer);
            case "judge":
                return gradeJudge(q, userAnswer);
            case "blank":
                return gradeBlank(q, userAnswer);
            case "essay":
                return new GradeResult(GradeResult.Grade.SELF_EVAL, q.answer, q.analysis);
            default:
                throw new IllegalArgumentException("unknown type: " + type);
        }
    }

    private static GradeResult gradeSingle(Question q, String user) {
        String correct = normalizeLetter(q.answer);
        boolean ok = user != null && !user.trim().isEmpty()
                && normalizeLetter(user).equals(correct);
        return new GradeResult(ok ? GradeResult.Grade.CORRECT : GradeResult.Grade.WRONG,
                q.answer, q.analysis);
    }

    private static GradeResult gradeMultiple(Question q, String user) {
        List<Character> correct = sortedLetters(q.answer);
        List<Character> picked = sortedLetters(user == null ? "" : user);
        if (picked.isEmpty()) {
            return new GradeResult(GradeResult.Grade.WRONG, q.answer, q.analysis);
        }
        if (picked.equals(correct)) {
            return new GradeResult(GradeResult.Grade.CORRECT, q.answer, q.analysis);
        }
        if (correct.containsAll(picked)) {
            return new GradeResult(GradeResult.Grade.PARTIAL, q.answer, q.analysis);
        }
        return new GradeResult(GradeResult.Grade.WRONG, q.answer, q.analysis);
    }

    private static GradeResult gradeJudge(Question q, String user) {
        String correct = judgeValue(q.answer);
        String picked = judgeValue(user);
        boolean ok = !picked.isEmpty() && picked.equals(correct);
        return new GradeResult(ok ? GradeResult.Grade.CORRECT : GradeResult.Grade.WRONG,
                correct, q.analysis);
    }

    private static GradeResult gradeBlank(Question q, String user) {
        String[] correct = q.answer == null ? new String[0] : q.answer.split("\\|", -1);
        String[] picked = user == null ? new String[0] : user.split("\\|", -1);
        boolean ok = correct.length > 0 && picked.length == correct.length;
        if (ok) {
            for (int i = 0; i < correct.length; i++) {
                if (!correct[i].trim().equalsIgnoreCase(picked[i].trim())) {
                    ok = false;
                    break;
                }
            }
        }
        return new GradeResult(ok ? GradeResult.Grade.CORRECT : GradeResult.Grade.WRONG,
                q.answer, q.analysis);
    }

    /** "A, C" / "ac" / " A C " -> "AC". */
    public static String normalizeLetter(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toUpperCase(Locale.ROOT).toCharArray()) {
            if (c >= 'A' && c <= 'Z' && sb.indexOf(String.valueOf(c)) < 0) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static List<Character> sortedLetters(String raw) {
        List<Character> list = new ArrayList<>();
        for (char c : normalizeLetter(raw).toCharArray()) {
            list.add(c);
        }
        Collections.sort(list);
        return list;
    }

    private static String judgeValue(String raw) {
        if (raw == null) {
            return "";
        }
        String v = raw.trim().toUpperCase(Locale.ROOT);
        if (v.equals("T") || v.equals("TRUE") || v.equals("对") || v.equals("正确")
                || v.equals("√") || v.equals("是")) {
            return "T";
        }
        if (v.equals("F") || v.equals("FALSE") || v.equals("错") || v.equals("错误")
                || v.equals("×") || v.equals("否")) {
            return "F";
        }
        return "";
    }
}
