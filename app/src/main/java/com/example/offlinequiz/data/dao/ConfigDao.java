package com.example.offlinequiz.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.offlinequiz.data.entity.AppConfig;

@Dao
public interface ConfigDao {

    @Query("SELECT value FROM app_config WHERE `key` = :key LIMIT 1")
    String get(String key);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void put(AppConfig config);

    @Query("DELETE FROM app_config")
    void deleteAll();
}
