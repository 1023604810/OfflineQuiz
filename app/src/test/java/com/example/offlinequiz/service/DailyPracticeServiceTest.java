package com.example.offlinequiz.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;

import com.example.offlinequiz.App;
import com.example.offlinequiz.data.entity.DailyPractice;
import com.example.offlinequiz.data.entity.Question;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;

import java.util.concurrent.Callable;

/** Daily practice: same date -> same set, progress persists and resumes. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class DailyPracticeServiceTest {

    private static <T> T onWorker(Callable<T> task) throws Exception {
        final Object[] result = new Object[1];
        final Throwable[] error = new Throwable[1];
        Thread thread = new Thread(() -> {
            try {
                result[0] = task.call();
            } catch (Throwable t) {
                error[0] = t;
            }
        });
        thread.start();
        thread.join();
        if (error[0] != null) {
            throw new AssertionError(error[0]);
        }
        //noinspection unchecked
        return (T) result[0];
    }

    private static void seed(App app, int count) {
        for (int i = 0; i < count; i++) {
            Question q = new Question();
            q.stem = "题目 " + i;
            q.type = "single";
            q.options = "[\"A. 甲\",\"B. 乙\"]";
            q.answer = "A";
            q.knowledge = "测试";
            q.contentHash = "daily-hash-" + i;
            q.createdAt = System.currentTimeMillis();
            app.db().questionDao().insert(q);
        }
    }

    @Test
    public void sameDateKeepsTheSameSetAndProgress() throws Exception {
        App app = ApplicationProvider.getApplicationContext();
        DailyPracticeService service =
                new DailyPracticeService(app.db().dailyDao(), app.db().questionDao());

        DailyPractice first = onWorker(() -> {
            app.db().questionDao().deleteAll();
            app.db().dailyDao().deleteAll();
            seed(app, 6);
            return service.getOrCreate("2026-10-30", 3);
        });

        assertEquals(3, first.totalCount);
        assertEquals(0, first.doneCount);
        assertTrue(first.questionIds.startsWith("["));

        onWorker(() -> {
            service.markAnswered(first, true);
            service.markAnswered(first, false);
            return null;
        });

        DailyPractice again = onWorker(() -> service.getOrCreate("2026-10-30", 3));
        assertEquals(first.questionIds, again.questionIds);
        assertEquals(2, again.doneCount);
        assertEquals(1, again.correctCount);

        //题库不足时按实际数量出齐
        DailyPractice small = onWorker(() -> service.getOrCreate("2026-10-31", 10));
        assertEquals(6, small.totalCount);
    }
}
