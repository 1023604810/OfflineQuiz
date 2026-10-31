package com.example.offlinequiz.service;

import com.example.offlinequiz.data.dao.ExamDao;
import com.example.offlinequiz.data.dao.QuestionDao;
import com.example.offlinequiz.data.entity.ExamSession;
import com.example.offlinequiz.data.entity.Question;
import com.example.offlinequiz.engine.AnswerEngine;
import com.example.offlinequiz.engine.ExamResult;
import com.example.offlinequiz.engine.GradeResult;
import com.example.offlinequiz.util.QuestionText;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Builds a paper by stratified random sampling (type x difficulty, optional knowledge
 * filter) and grades a submitted one.
 */
public class ExamService {

    /** 单选/判断 1 分，多选 2 分，填空每空 0.5 分，主观题不计入自动评分。 */
    public static final double SCORE_SINGLE = 1;
    public static final double SCORE_JUDGE = 1;
    public static final double SCORE_MULTIPLE = 2;
    public static final double SCORE_BLANK_PER_BLANK = 0.5;

    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();

    private final QuestionDao questionDao;
    private final ExamDao examDao;
    private final Gson gson = new Gson();

    public ExamService(QuestionDao questionDao, ExamDao examDao) {
        this.questionDao = questionDao;
        this.examDao = examDao;
    }

    /** Blocking call; run it off the main thread. */
    public ExamSession buildPaper(ExamConfig cfg) {
        List<Question> paper = new ArrayList<>();
        List<String> shortages = new ArrayList<>();
        List<Integer> difficulties = cfg.difficulties == null || cfg.difficulties.isEmpty()
                ? java.util.Arrays.asList(1, 2, 3) : cfg.difficulties;
        Random random = new Random();

        for (Map.Entry<String, Integer> entry : cfg.counts.entrySet()) {
            String type = entry.getKey();
            int need = entry.getValue() == null ? 0 : entry.getValue();
            if (need <= 0) {
                continue;
            }
            List<Question> pool = new ArrayList<>(
                    questionDao.findByTypeAndDifficulty(type, difficulties));
            pool = filterKnowledge(pool, cfg.knowledge);
            Collections.shuffle(pool, random);
            if (pool.size() < need) {
                // fallback 1: relax the difficulty constraint
                List<Question> relaxed = new ArrayList<>(questionDao.findByType(type));
                relaxed = filterKnowledge(relaxed, cfg.knowledge);
                Collections.shuffle(relaxed, random);
                if (relaxed.size() < need) {
                    shortages.add(QuestionText.typeLabel(type) + "仅有 " + relaxed.size()
                            + " 题，不足配置的 " + need + " 题");
                }
                pool = relaxed;
            }
            paper.addAll(pool.subList(0, Math.min(need, pool.size())));
        }
        Collections.shuffle(paper, random);

        ExamSession session = new ExamSession();
        session.title = cfg.title;
        session.questionIds = gson.toJson(idsOf(paper));
        session.timeLimit = cfg.durationMinutes;
        session.startTime = System.currentTimeMillis();
        session.status = 0;
        session.totalScore = 0;
        for (Question q : paper) {
            session.totalScore += fullScore(q);
        }
        session.shortageNote = shortages.isEmpty() ? null : join(shortages);
        session.id = examDao.insert(session);
        return session;
    }

    public List<Question> loadPaper(ExamSession session) {
        List<Long> ids = idsOf(session);
        List<Question> loaded = questionDao.getByIds(ids);
        Map<Long, Question> byId = new HashMap<>();
        for (Question q : loaded) {
            byId.put(q.id, q);
        }
        List<Question> ordered = new ArrayList<>();
        for (Long id : ids) {
            Question q = byId.get(id);
            if (q != null) {
                ordered.add(q);
            }
        }
        return ordered;
    }

    public void saveAnswers(ExamSession session, Map<Long, String> answers) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<Long, String> e : answers.entrySet()) {
            if (e.getValue() != null) {
                map.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        session.answers = gson.toJson(map);
        examDao.update(session);
    }

    public static Map<Long, String> parseAnswers(ExamSession session) {
        Map<Long, String> out = new LinkedHashMap<>();
        if (session == null || session.answers == null) {
            return out;
        }
        Map<String, String> map = new Gson().fromJson(session.answers, MAP_TYPE);
        if (map != null) {
            for (Map.Entry<String, String> e : map.entrySet()) {
                try {
                    out.put(Long.parseLong(e.getKey()), e.getValue());
                } catch (NumberFormatException ignored) {
                    // skip malformed entry
                }
            }
        }
        return out;
    }

    /** Grades the paper, persists the result and returns it. Blocking call. */
    public ExamResult submit(ExamSession session, Map<Long, String> answers) {
        ExamResult result = evaluate(session, answers);
        session.userScore = round1(result.userScore);
        session.submitTime = System.currentTimeMillis();
        session.status = 1;
        session.answers = new Gson().toJson(answerMap(answers));
        examDao.update(session);
        return result;
    }

    /** Pure grading used both by submit and by the result page (no DB writes). */
    public ExamResult evaluate(ExamSession session, Map<Long, String> answers) {
        List<Question> paper = loadPaper(session);
        ExamResult result = new ExamResult();
        result.totalQuestions = paper.size();
        result.totalScore = session.totalScore;
        result.shortageNote = session.shortageNote;

        Map<String, double[]> byType = new LinkedHashMap<>();
        Map<String, int[]> knowledgeStat = new LinkedHashMap<>();

        for (Question q : paper) {
            String userAnswer = answers.get(q.id);
            String type = q.type == null ? "single" : q.type;
            double full = fullScore(q);
            double got = 0;
            int correctFlag = 0;

            if (userAnswer == null || userAnswer.trim().isEmpty()) {
                result.unanswered++;
            } else if ("essay".equals(type)) {
                // 主观题不自动判分，标注需自评
                result.correctCount++;
                correctFlag = 1;
            } else if ("blank".equals(type)) {
                double[] blankScore = gradeBlankScore(q, userAnswer);
                got = blankScore[0];
                if (got >= full) {
                    result.correctCount++;
                    correctFlag = 1;
                } else if (got > 0) {
                    result.partialCount++;
                    correctFlag = 2;
                } else {
                    result.wrongCount++;
                }
            } else {
                GradeResult grade = AnswerEngine.grade(q, userAnswer);
                if (grade.grade == GradeResult.Grade.CORRECT) {
                    got = full;
                    result.correctCount++;
                    correctFlag = 1;
                } else if (grade.grade == GradeResult.Grade.PARTIAL) {
                    got = full / 2;
                    result.partialCount++;
                    correctFlag = 2;
                } else {
                    result.wrongCount++;
                }
            }

            result.userScore += got;
            double[] stat = byType.get(type);
            if (stat == null) {
                stat = new double[3];
                byType.put(type, stat);
            }
            stat[0] += 1;
            stat[1] += correctFlag == 1 ? 1 : 0;
            stat[2] += got;

            String k = q.knowledge == null || q.knowledge.isEmpty() ? "未分类" : q.knowledge;
            int[] kstat = knowledgeStat.get(k);
            if (kstat == null) {
                kstat = new int[2];
                knowledgeStat.put(k, kstat);
            }
            kstat[0] += 1;
            kstat[1] += correctFlag == 1 ? 1 : 0;
        }
        result.byType.putAll(byType);
        for (Map.Entry<String, int[]> e : knowledgeStat.entrySet()) {
            int total = e.getValue()[0];
            int correct = e.getValue()[1];
            if (total >= 2 && correct * 100f / total < 50f) {
                result.weakKnowledge.add(e.getKey());
            }
        }
        Collections.sort(result.weakKnowledge);
        return result;
    }

    /** Number of questions stored in a paper snapshot. */
    public static int questionCount(ExamSession session) {
        if (session == null || session.questionIds == null) {
            return 0;
        }
        long[] ids = new Gson().fromJson(session.questionIds, long[].class);
        return ids == null ? 0 : ids.length;
    }

    public static double fullScore(Question q) {
        String type = q.type == null ? "single" : q.type;
        switch (type) {
            case "multiple":
                return SCORE_MULTIPLE;
            case "blank":
                return SCORE_BLANK_PER_BLANK * QuestionText.blankCount(q);
            case "essay":
                return 0;
            case "judge":
                return SCORE_JUDGE;
            default:
                return SCORE_SINGLE;
        }
    }

    private static double[] gradeBlankScore(Question q, String userAnswer) {
        String[] correct = q.answer == null ? new String[0] : q.answer.split("\\|", -1);
        String[] picked = userAnswer.split("\\|", -1);
        double got = 0;
        for (int i = 0; i < correct.length; i++) {
            if (i < picked.length && correct[i].trim().equalsIgnoreCase(picked[i].trim())) {
                got += SCORE_BLANK_PER_BLANK;
            }
        }
        return new double[]{got};
    }

    private static List<Long> idsOf(List<Question> questions) {
        List<Long> ids = new ArrayList<>();
        for (Question q : questions) {
            ids.add(q.id);
        }
        return ids;
    }

    private static List<Long> idsOf(ExamSession session) {
        List<Long> ids = new ArrayList<>();
        long[] raw = new Gson().fromJson(session.questionIds, long[].class);
        if (raw != null) {
            for (long id : raw) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static List<Question> filterKnowledge(List<Question> in, String knowledge) {
        if (knowledge == null || knowledge.trim().isEmpty()) {
            return in;
        }
        List<Question> out = new ArrayList<>();
        for (Question q : in) {
            if (q.knowledge != null && q.knowledge.contains(knowledge)) {
                out.add(q);
            }
        }
        return out;
    }

    private static Map<String, String> answerMap(Map<Long, String> answers) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<Long, String> e : answers.entrySet()) {
            if (e.getValue() != null) {
                map.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return map;
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(p);
        }
        return sb.toString();
    }
}
