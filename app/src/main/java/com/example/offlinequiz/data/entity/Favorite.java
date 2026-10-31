package com.example.offlinequiz.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;

@Entity(tableName = "favorites",
        foreignKeys = @ForeignKey(entity = Question.class,
                parentColumns = "id", childColumns = "question_id",
                onDelete = CASCADE),
        indices = {@Index(value = "question_id", unique = true)})
public class Favorite {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "question_id")
    public long questionId;

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
