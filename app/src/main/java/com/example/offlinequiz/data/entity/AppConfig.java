package com.example.offlinequiz.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Simple key/value store (settings that belong to the bank, import metadata...). */
@Entity(tableName = "app_config")
public class AppConfig {

    @PrimaryKey
    @NonNull
    public String key = "";

    public String value;

    public AppConfig() {
    }

    @androidx.room.Ignore
    public AppConfig(@NonNull String key, String value) {
        this.key = key;
        this.value = value;
    }
}
