package com.example.offlinequiz.util;

import com.example.offlinequiz.data.entity.Question;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Text helpers for question rendering. */
public final class QuestionText {

    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<String>>() {
    }.getType();

    private QuestionText() {
    }

    public static List<String> optionList(Question q) {
        if (q.options == null || q.options.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<String> list = GSON.fromJson(q.options, LIST_TYPE);
            return list == null ? Collections.emptyList() : list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** Splits "A. 选项内容" into its letter and body. */
    public static String[] splitOption(String option) {
        String o = option == null ? "" : option.trim();
        if (o.length() >= 2 && o.charAt(0) >= 'A' && o.charAt(0) <= 'H'
                && (o.charAt(1) == '.' || o.charAt(1) == '．' || o.charAt(1) == '、'
                || o.charAt(1) == ' ' || o.charAt(1) == ':' || o.charAt(1) == '：')) {
            String text = o.substring(2).trim();
            if (text.startsWith(".") || text.startsWith("．")) {
                text = text.substring(1).trim();
            }
            return new String[]{String.valueOf(o.charAt(0)), text};
        }
        return new String[]{"", o};
    }

    public static String typeLabel(String type) {
        if (type == null) {
            return "单选题";
        }
        switch (type) {
            case "multiple":
                return "多选题";
            case "judge":
                return "判断题";
            case "blank":
                return "填空题";
            case "essay":
                return "主观题";
            default:
                return "单选题";
        }
    }

    public static String difficultyLabel(int difficulty) {
        switch (difficulty) {
            case 1:
                return "简单";
            case 3:
                return "困难";
            default:
                return "中等";
        }
    }

    public static String answerDisplay(Question q, String answer) {
        if (answer == null) {
            return "";
        }
        if ("judge".equals(q.type)) {
            return "T".equalsIgnoreCase(answer.trim()) ? "正确" : "错误";
        }
        return answer;
    }

    /** Number of blanks in a fill-in question. */
    public static int blankCount(Question q) {
        if (q.answer == null || q.answer.isEmpty()) {
            return 1;
        }
        return q.answer.split("\\|", -1).length;
    }

    public static List<String> knowledgePath(String knowledge) {
        List<String> out = new ArrayList<>();
        if (knowledge == null || knowledge.trim().isEmpty()) {
            return out;
        }
        for (String s : knowledge.split("/")) {
            if (!s.trim().isEmpty()) {
                out.add(s.trim());
            }
        }
        return out;
    }
}
