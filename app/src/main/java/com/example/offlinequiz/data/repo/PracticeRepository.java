package com.example.offlinequiz.data.repo;

import com.example.offlinequiz.data.AppDatabase;
import com.example.offlinequiz.data.entity.Favorite;
import com.example.offlinequiz.data.entity.PracticeRecord;
import com.example.offlinequiz.data.entity.Question;
import com.example.offlinequiz.data.entity.WrongQuestion;
import com.example.offlinequiz.data.model.FavoriteItem;
import com.example.offlinequiz.data.model.WrongItem;

import androidx.lifecycle.LiveData;

import java.util.List;

/**
 * Everything that happens while answering: practice records, the automatic wrong
 * book, favorites. All methods are blocking; callers run them on an IO executor.
 */
public class PracticeRepository {

    private final AppDatabase db;

    public PracticeRepository(AppDatabase db) {
        this.db = db;
    }

    public void recordAnswer(Question q, String userAnswer, int flag, int timeCost) {
        db.practiceDao().insert(new PracticeRecord(q.id, userAnswer, flag, timeCost,
                System.currentTimeMillis()));
        if (flag == 0) {
            WrongQuestion existing = db.wrongDao().getByQuestion(q.id);
            if (existing == null) {
                WrongQuestion w = new WrongQuestion();
                w.questionId = q.id;
                w.wrongCount = 1;
                w.lastWrongAt = System.currentTimeMillis();
                w.resolved = 0;
                db.wrongDao().insert(w);
            } else {
                existing.wrongCount++;
                existing.lastWrongAt = System.currentTimeMillis();
                existing.resolved = 0;
                db.wrongDao().update(existing);
            }
        } else if (flag == 1) {
            WrongQuestion existing = db.wrongDao().getByQuestion(q.id);
            if (existing != null) {
                existing.correctCount++;
                db.wrongDao().update(existing);
            }
        }
    }

    public boolean toggleFavorite(long questionId) {
        if (db.favoriteDao().isFavorite(questionId) > 0) {
            db.favoriteDao().deleteByQuestion(questionId);
            return false;
        }
        Favorite f = new Favorite();
        f.questionId = questionId;
        f.createdAt = System.currentTimeMillis();
        db.favoriteDao().insert(f);
        return true;
    }

    public boolean isFavorite(long questionId) {
        return db.favoriteDao().isFavorite(questionId) > 0;
    }

    public void removeWrong(long questionId) {
        db.wrongDao().deleteByQuestion(questionId);
    }

    public LiveData<List<WrongItem>> wrongItems() {
        return db.wrongDao().itemsLive();
    }

    public LiveData<List<FavoriteItem>> favoriteItems() {
        return db.favoriteDao().itemsLive();
    }

    public List<Long> wrongQuestionIds() {
        return db.wrongDao().orderedQuestionIds();
    }

    public List<Long> favoriteQuestionIds() {
        return db.favoriteDao().orderedQuestionIds();
    }

    public int wrongCount() {
        return db.wrongDao().count();
    }

    public int favoriteCount() {
        return db.favoriteDao().count();
    }
}
