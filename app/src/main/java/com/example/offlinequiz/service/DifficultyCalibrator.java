package com.example.offlinequiz.service;

import com.example.offlinequiz.data.AppDatabase;
import com.example.offlinequiz.data.entity.AppConfig;
import com.example.offlinequiz.data.entity.Question;
import com.example.offlinequiz.data.model.KnowledgeStat;
import com.example.offlinequiz.data.model.QuestionStat;
import com.example.offlinequiz.engine.AnalysisFactory;
import com.example.offlinequiz.engine.DifficultyAnalyzer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies {@link DifficultyAnalyzer} to every question in the bank and back-fills
 * missing explanations. Runs after import and once on app start when the bank changed;
 * the result is persisted, so the difficulty chips, filters and exam papers all use
 * the calibrated values.
 */
public class DifficultyCalibrator {

    public static final String CONFIG_KEY = "difficulty_calibration";
    public static final int VERSION = 2;

    private final AppDatabase db;

    public DifficultyCalibrator(AppDatabase db) {
        this.db = db;
    }

    /**
     * @return number of questions processed (0 when the bank is unchanged and force=false)
     */
    public int calibrate(boolean force) {
        List<Question> questions = db.questionDao().getAllOrdered();
        if (questions.isEmpty()) {
            return 0;
        }
        String marker = questions.size() + ":" + VERSION;
        if (!force && marker.equals(db.configDao().get(CONFIG_KEY))) {
            return 0;
        }

        Map<Long, QuestionStat> questionStats = new HashMap<>();
        for (QuestionStat stat : db.practiceDao().questionStats()) {
            questionStats.put(stat.questionId, stat);
        }
        Map<String, KnowledgeStat> knowledgeStats = new HashMap<>();
        for (KnowledgeStat stat : db.practiceDao().knowledgeStats()) {
            knowledgeStats.put(stat.knowledge, stat);
        }

        db.runInTransaction(() -> {
            for (Question q : questions) {
                QuestionStat qs = questionStats.get(q.id);
                KnowledgeStat ks = q.knowledge == null ? null : knowledgeStats.get(q.knowledge);
                DifficultyAnalyzer.Breakdown breakdown = DifficultyAnalyzer.analyze(
                        q,
                        qs == null ? 0 : qs.attempts,
                        qs == null ? 0 : qs.wrong,
                        ks == null ? 0 : ks.attempts,
                        ks == null ? 0 : ks.wrong);
                if (q.analysis == null || q.analysis.trim().isEmpty()) {
                    db.questionDao().updateAnalysis(q.id, AnalysisFactory.generate(q));
                }
                db.questionDao().updateDifficulty(q.id, breakdown.level, breakdown.score,
                        breakdown.reason);
            }
        });

        db.configDao().put(new AppConfig(CONFIG_KEY, marker));
        return questions.size();
    }
}
