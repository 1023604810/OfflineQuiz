package com.example.offlinequiz.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.offlinequiz.data.entity.ExamSession;

import java.util.List;

@Dao
public interface ExamDao {

    @Insert
    long insert(ExamSession session);

    @Update
    void update(ExamSession session);

    @Query("SELECT * FROM exam_sessions WHERE id = :id")
    ExamSession getById(long id);

    @Query("DELETE FROM exam_sessions WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT * FROM exam_sessions WHERE status = 0 ORDER BY start_time DESC LIMIT 1")
    ExamSession unfinished();

    @Query("SELECT * FROM exam_sessions WHERE status = 1 ORDER BY submit_time DESC LIMIT :limit")
    List<ExamSession> history(int limit);

    @Query("SELECT * FROM exam_sessions WHERE status = 1 ORDER BY submit_time DESC LIMIT :limit")
    LiveData<List<ExamSession>> historyLive(int limit);

    @Query("DELETE FROM exam_sessions")
    void deleteAll();
}
