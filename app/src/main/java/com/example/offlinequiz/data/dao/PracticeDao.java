package com.example.offlinequiz.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.offlinequiz.data.entity.PracticeRecord;

import java.util.List;

@Dao
public interface PracticeDao {

    @Insert
    long insert(PracticeRecord record);

    @Query("SELECT COUNT(*) FROM practice_records")
    int totalCount();

    @Query("SELECT COUNT(*) FROM practice_records")
    LiveData<Integer> totalCountLive();

    @Query("SELECT COUNT(*) FROM practice_records WHERE is_correct = 1")
    int correctCount();

    @Query("SELECT COUNT(*) FROM practice_records WHERE is_correct = 1")
    LiveData<Integer> correctCountLive();

    @Query("SELECT * FROM practice_records WHERE created_at >= :since ORDER BY created_at ASC")
    List<PracticeRecord> since(long since);

    @Query("SELECT COUNT(*) FROM practice_records WHERE question_id = :questionId")
    int countForQuestion(long questionId);

    @Query("SELECT SUM(time_cost) FROM practice_records")
    Integer totalSeconds();

    @Query("SELECT q.knowledge AS knowledge, COUNT(*) AS total, "
            + "SUM(CASE WHEN pr.is_correct = 1 THEN 1 ELSE 0 END) AS correct, "
            + "ROUND(100.0 * SUM(CASE WHEN pr.is_correct = 1 THEN 1 ELSE 0 END) / COUNT(*), 1) AS mastery "
            + "FROM practice_records pr JOIN questions q ON q.id = pr.question_id "
            + "WHERE q.knowledge IS NOT NULL AND q.knowledge <> '' "
            + "GROUP BY q.knowledge HAVING COUNT(*) >= 3 ORDER BY mastery ASC LIMIT 10")
    List<com.example.offlinequiz.data.model.MasteryRow> mastery();

    @Query("SELECT pr.question_id AS questionId, COUNT(*) AS attempts, "
            + "SUM(CASE WHEN pr.is_correct = 0 THEN 1 ELSE 0 END) AS wrong "
            + "FROM practice_records pr GROUP BY pr.question_id")
    List<com.example.offlinequiz.data.model.QuestionStat> questionStats();

    @Query("SELECT q.knowledge AS knowledge, COUNT(*) AS attempts, "
            + "SUM(CASE WHEN pr.is_correct = 0 THEN 1 ELSE 0 END) AS wrong "
            + "FROM practice_records pr JOIN questions q ON q.id = pr.question_id "
            + "WHERE q.knowledge IS NOT NULL AND q.knowledge <> '' GROUP BY q.knowledge")
    List<com.example.offlinequiz.data.model.KnowledgeStat> knowledgeStats();

    @Query("DELETE FROM practice_records")
    void deleteAll();
}
