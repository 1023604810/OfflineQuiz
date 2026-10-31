package com.example.offlinequiz.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.offlinequiz.data.entity.DailyPractice;

import java.util.List;

@Dao
public interface DailyDao {

    @Query("SELECT * FROM daily_practice WHERE date_key = :dateKey LIMIT 1")
    DailyPractice getByDate(String dateKey);

    @Query("SELECT * FROM daily_practice ORDER BY date_key DESC")
    List<DailyPractice> all();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(DailyPractice daily);

    @Update
    void update(DailyPractice daily);

    @Query("DELETE FROM daily_practice")
    void deleteAll();
}
