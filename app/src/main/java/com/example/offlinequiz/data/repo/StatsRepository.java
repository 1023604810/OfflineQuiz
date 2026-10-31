package com.example.offlinequiz.data.repo;

import com.example.offlinequiz.data.AppDatabase;
import com.example.offlinequiz.data.entity.PracticeRecord;
import com.example.offlinequiz.data.model.DayStat;
import com.example.offlinequiz.data.model.MasteryRow;
import com.example.offlinequiz.util.DateUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Aggregates practice records into day/week/month figures and mastery rows. */
public class StatsRepository {

    private final AppDatabase db;

    public StatsRepository(AppDatabase db) {
        this.db = db;
    }

    /** Oldest first, one entry per day including empty days. */
    public List<DayStat> recentDays(int days) {
        long from = DateUtil.startOfDay(System.currentTimeMillis(), -(days - 1));
        List<PracticeRecord> records = db.practiceDao().since(from);
        Map<String, DayStat> byDay = new HashMap<>();
        List<DayStat> out = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            long start = DateUtil.startOfDay(System.currentTimeMillis(), -(days - 1 - i));
            DayStat stat = new DayStat();
            stat.dayStart = start;
            stat.label = DateUtil.shortDay(start);
            out.add(stat);
            byDay.put(DateUtil.keyOf(start), stat);
        }
        for (PracticeRecord r : records) {
            DayStat stat = byDay.get(DateUtil.keyOf(r.createdAt));
            if (stat != null) {
                stat.total++;
                if (r.isCorrect == 1) {
                    stat.correct++;
                }
            }
        }
        return out;
    }

    public List<MasteryRow> mastery() {
        List<MasteryRow> rows = db.practiceDao().mastery();
        return rows == null ? new ArrayList<>() : rows;
    }

    public int totalAnswers() {
        return db.practiceDao().totalCount();
    }

    public int correctAnswers() {
        return db.practiceDao().correctCount();
    }

    public int questionCount() {
        return db.questionDao().count();
    }
}
