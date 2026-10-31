package com.example.offlinequiz.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.offlinequiz.data.entity.Question;

import java.util.List;

@Dao
public interface QuestionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(Question question);

    @Update
    int update(Question question);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    List<Long> insertAll(List<Question> questions);

    @Query("SELECT * FROM questions WHERE stem_key = :key LIMIT 1")
    Question findByStemKey(String key);

    @Query("SELECT * FROM questions WHERE stem_key IS NULL OR stem_key = ''")
    List<Question> withoutStemKey();

    @Query("UPDATE questions SET stem_key = :key WHERE id = :id")
    int updateStemKey(long id, String key);

    @Query("DELETE FROM questions WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT COUNT(*) FROM questions WHERE stem_key IS NOT NULL AND stem_key <> ''")
    int countWithStemKey();

    @Query("SELECT COUNT(DISTINCT stem_key) FROM questions")
    int countDistinctStemKeys();

    @Query("SELECT COUNT(*) FROM questions")
    int count();

    @Query("SELECT COUNT(*) FROM questions")
    LiveData<Integer> countLive();

    @Query("SELECT COUNT(*) FROM questions WHERE type = :type")
    int countByType(String type);

    @Query("SELECT id FROM questions")
    List<Long> allIds();

    @Query("SELECT * FROM questions WHERE id = :id")
    Question getById(long id);

    /** All questions in a stable order - used by "开始练习（全部题目）". */
    @Query("SELECT * FROM questions ORDER BY id ASC")
    List<Question> getAllOrdered();

    @Query("SELECT * FROM questions WHERE analysis IS NULL OR TRIM(analysis) = ''")
    List<Question> withoutAnalysis();

    @Query("UPDATE questions SET analysis = :analysis WHERE id = :id")
    int updateAnalysis(long id, String analysis);

    @Query("UPDATE questions SET difficulty = :level, difficulty_score = :score, "
            + "difficulty_reason = :reason WHERE id = :id")
    int updateDifficulty(long id, int level, int score, String reason);

    @Query("SELECT * FROM questions WHERE id IN (:ids)")
    List<Question> getByIds(List<Long> ids);

    @Query("SELECT * FROM questions WHERE type = :type")
    List<Question> findByType(String type);

    @Query("SELECT * FROM questions WHERE type = :type AND difficulty IN (:difficulties)")
    List<Question> findByTypeAndDifficulty(String type, List<Integer> difficulties);

    @Query("SELECT * FROM questions WHERE knowledge = :knowledge")
    List<Question> findByKnowledge(String knowledge);

    @Query("SELECT * FROM questions ORDER BY RANDOM() LIMIT :limit")
    List<Question> random(int limit);

    @Query("SELECT * FROM questions WHERE difficulty = :difficulty ORDER BY RANDOM() LIMIT :limit")
    List<Question> randomByDifficulty(int difficulty, int limit);

    @Query("SELECT DISTINCT knowledge FROM questions WHERE knowledge IS NOT NULL AND knowledge <> '' ORDER BY knowledge")
    List<String> distinctKnowledge();

    @Query("SELECT content_hash FROM questions WHERE content_hash = :hash LIMIT 1")
    String findHash(String hash);

    @Query("SELECT content_hash FROM questions")
    List<String> allHashes();

    @Query("DELETE FROM questions")
    void deleteAll();
}
