package com.example.offlinequiz.util;

import android.content.Context;
import android.content.SharedPreferences;

/** App settings stored in SharedPreferences (no DB access needed on the UI thread). */
public class Prefs {

    private static final String FILE = "offline_quiz_prefs";
    private static final String KEY_DAILY_COUNT = "daily_count";
    private static final String KEY_RECITE_MODE = "recite_mode";
    private static final String KEY_ALL_INDEX_ID = "all_practice_question_id";
    private static final String KEY_ALL_COUNT = "all_practice_question_count";

    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public int dailyCount() {
        return sp.getInt(KEY_DAILY_COUNT, 10);
    }

    public void setDailyCount(int count) {
        sp.edit().putInt(KEY_DAILY_COUNT, count).apply();
    }

    /** true = 背题模式（自动显示答案）, false = 刷题模式（先作答再判分）. */
    public boolean reciteMode() {
        return sp.getBoolean(KEY_RECITE_MODE, false);
    }

    public void setReciteMode(boolean recite) {
        sp.edit().putBoolean(KEY_RECITE_MODE, recite).apply();
    }

    /** Last question shown in "练习全部题目", used to resume without restarting. */
    public long allPracticeQuestionId() {
        return sp.getLong(KEY_ALL_INDEX_ID, -1);
    }

    public int allPracticeQuestionCount() {
        return sp.getInt(KEY_ALL_COUNT, 0);
    }

    public void saveAllPracticeProgress(long questionId, int questionCount) {
        sp.edit()
                .putLong(KEY_ALL_INDEX_ID, questionId)
                .putInt(KEY_ALL_COUNT, questionCount)
                .apply();
    }

    public void clearAllPracticeProgress() {
        sp.edit()
                .remove(KEY_ALL_INDEX_ID)
                .remove(KEY_ALL_COUNT)
                .apply();
    }
}
