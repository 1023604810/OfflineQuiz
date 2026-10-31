package com.example.offlinequiz.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.offlinequiz.data.entity.WrongQuestion;
import com.example.offlinequiz.data.model.WrongItem;

import java.util.List;

@Dao
public interface WrongDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(WrongQuestion wrong);

    @Update
    void update(WrongQuestion wrong);

    @Query("SELECT * FROM wrong_questions WHERE question_id = :questionId")
    WrongQuestion getByQuestion(long questionId);

    @Query("DELETE FROM wrong_questions WHERE question_id = :questionId")
    void deleteByQuestion(long questionId);

    @Query("DELETE FROM wrong_questions")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM wrong_questions WHERE resolved = 0")
    LiveData<Integer> countLive();

    @Query("SELECT COUNT(*) FROM wrong_questions WHERE resolved = 0")
    int count();

    @Query("SELECT q.id AS questionId, q.stem AS stem, q.type AS type, q.knowledge AS knowledge, "
            + "w.wrong_count AS wrongCount, w.last_wrong_at AS lastWrongAt "
            + "FROM wrong_questions w JOIN questions q ON q.id = w.question_id "
            + "WHERE w.resolved = 0 ORDER BY w.wrong_count DESC, w.last_wrong_at DESC")
    LiveData<List<WrongItem>> itemsLive();

    @Query("SELECT w.question_id FROM wrong_questions w "
            + "WHERE w.resolved = 0 ORDER BY w.wrong_count DESC, w.last_wrong_at DESC")
    List<Long> orderedQuestionIds();
}
