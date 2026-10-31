package com.example.offlinequiz.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.offlinequiz.data.entity.Favorite;
import com.example.offlinequiz.data.model.FavoriteItem;

import java.util.List;

@Dao
public interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Favorite favorite);

    @Query("DELETE FROM favorites WHERE question_id = :questionId")
    void deleteByQuestion(long questionId);

    @Query("DELETE FROM favorites")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM favorites WHERE question_id = :questionId")
    int isFavorite(long questionId);

    @Query("SELECT COUNT(*) FROM favorites")
    LiveData<Integer> countLive();

    @Query("SELECT COUNT(*) FROM favorites")
    int count();

    @Query("SELECT q.id AS questionId, q.stem AS stem, q.type AS type, q.knowledge AS knowledge, "
            + "f.created_at AS createdAt "
            + "FROM favorites f JOIN questions q ON q.id = f.question_id "
            + "ORDER BY f.created_at DESC")
    LiveData<List<FavoriteItem>> itemsLive();

    @Query("SELECT f.question_id FROM favorites f ORDER BY f.created_at DESC")
    List<Long> orderedQuestionIds();
}
