package com.example.offlinequiz.service;

import com.example.offlinequiz.data.dao.DailyDao;
import com.example.offlinequiz.data.dao.QuestionDao;
import com.example.offlinequiz.data.entity.DailyPractice;
import com.example.offlinequiz.util.DateUtil;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Daily practice: the question set is derived from the date itself, so re-entering
 * the same day always yields the same paper. Progress (done_count) is persisted and
 * used to resume at the first unanswered question.
 */
public class DailyPracticeService {

    private final DailyDao dailyDao;
    private final QuestionDao questionDao;
    private final Gson gson = new Gson();

    public DailyPracticeService(DailyDao dailyDao, QuestionDao questionDao) {
        this.dailyDao = dailyDao;
        this.questionDao = questionDao;
    }

    /** Blocking call; run it off the main thread. */
    public DailyPractice getOrCreate(String dateKey, int dailyCount) {
        DailyPractice cached = dailyDao.getByDate(dateKey);
        if (cached != null && cached.totalCount > 0) {
            return cached;
        }
        List<Long> ids = new ArrayList<>(questionDao.allIds());
        if (ids.isEmpty()) {
            return null;
        }
        Collections.shuffle(ids, new Random(DateUtil.dailySeed(dateKey)));
        int take = Math.min(Math.max(dailyCount, 1), ids.size());
        List<Long> picked = new ArrayList<>(ids.subList(0, take));

        DailyPractice daily = new DailyPractice();
        daily.dateKey = dateKey;
        daily.questionIds = gson.toJson(picked);
        daily.totalCount = picked.size();
        daily.doneCount = 0;
        daily.correctCount = 0;
        daily.id = dailyDao.insert(daily);
        return daily;
    }

    public List<Long> questionIds(DailyPractice daily) {
        if (daily == null || daily.questionIds == null) {
            return new ArrayList<>();
        }
        long[] ids = gson.fromJson(daily.questionIds, long[].class);
        List<Long> list = new ArrayList<>();
        if (ids != null) {
            for (long id : ids) {
                list.add(id);
            }
        }
        return list;
    }

    public void markAnswered(DailyPractice daily, boolean correct) {
        if (daily == null) {
            return;
        }
        daily.doneCount++;
        if (correct) {
            daily.correctCount++;
        }
        dailyDao.update(daily);
    }
}
