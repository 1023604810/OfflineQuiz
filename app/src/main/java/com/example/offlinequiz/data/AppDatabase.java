package com.example.offlinequiz.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Single Room database holding the question bank plus every piece of local
 * learning data (practice records, wrong answers, favorites, daily practice,
 * exam sessions, key/value config).
 */
@Database(
        entities = {
                com.example.offlinequiz.data.entity.Question.class,
                com.example.offlinequiz.data.entity.PracticeRecord.class,
                com.example.offlinequiz.data.entity.WrongQuestion.class,
                com.example.offlinequiz.data.entity.Favorite.class,
                com.example.offlinequiz.data.entity.DailyPractice.class,
                com.example.offlinequiz.data.entity.ExamSession.class,
                com.example.offlinequiz.data.entity.AppConfig.class
        },
        version = 3,
        exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    /** v2 adds the difficulty score/reason columns; existing learning data is kept. */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE questions ADD COLUMN difficulty_score INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE questions ADD COLUMN difficulty_reason TEXT");
        }
    };

    /** v3 adds the normalised stem key + unique index for exact-match upsert. */
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE questions ADD COLUMN stem_key TEXT");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_questions_stem_key "
                    + "ON questions(stem_key)");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "offline_quiz.db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public abstract com.example.offlinequiz.data.dao.QuestionDao questionDao();

    public abstract com.example.offlinequiz.data.dao.PracticeDao practiceDao();

    public abstract com.example.offlinequiz.data.dao.WrongDao wrongDao();

    public abstract com.example.offlinequiz.data.dao.FavoriteDao favoriteDao();

    public abstract com.example.offlinequiz.data.dao.DailyDao dailyDao();

    public abstract com.example.offlinequiz.data.dao.ExamDao examDao();

    public abstract com.example.offlinequiz.data.dao.ConfigDao configDao();
}
