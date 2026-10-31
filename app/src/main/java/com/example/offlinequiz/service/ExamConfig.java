package com.example.offlinequiz.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parameters for one mock paper. */
public class ExamConfig {

    public String title = "模拟考试";
    public int durationMinutes = 30;
    /** question type -> requested count (single / multiple / judge / blank / essay). */
    public Map<String, Integer> counts = new LinkedHashMap<>();
    public List<Integer> difficulties;
    /** Optional knowledge tag filter, null means the whole bank. */
    public String knowledge;

    public int totalQuestions() {
        int sum = 0;
        for (Integer v : counts.values()) {
            if (v != null) {
                sum += v;
            }
        }
        return sum;
    }
}
