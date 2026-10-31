package com.example.offlinequiz.ui.quiz;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.example.offlinequiz.App;
import com.example.offlinequiz.R;
import com.example.offlinequiz.data.entity.Question;
import com.example.offlinequiz.ui.widget.OptionGroupView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;

import java.util.concurrent.Callable;

/** 全部练习入口 + 背题/刷题实时切换（保留题号与选择）。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class QuizModeTest {

    private App app;

    private <T> T onWorker(Callable<T> task) throws Exception {
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

    @Before
    public void seed() throws Exception {
        app = ApplicationProvider.getApplicationContext();
        onWorker(() -> {
            app.db().questionDao().deleteAll();
            app.db().practiceDao().deleteAll();
            for (int i = 0; i < 3; i++) {
                Question q = new Question();
                q.stem = "测试题干 " + i;
                q.type = "single";
                q.options = "[\"A. 甲\",\"B. 乙\",\"C. 丙\",\"D. 丁\"]";
                q.answer = "A";
                q.analysis = "这是第 " + i + " 题的解析";
                q.knowledge = "测试";
                q.difficulty = 2;
                q.difficultyScore = 50;
                q.contentHash = "quiz-mode-" + i;
                q.createdAt = System.currentTimeMillis();
                app.db().questionDao().insert(q);
            }
            return null;
        });
        app.getSharedPreferences("offline_quiz_prefs", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("recite_mode", false)
                .putLong("all_practice_question_id", -1)
                .putInt("all_practice_question_count", 0)
                .apply();
    }

    @Test
    public void allPracticeLoadsEveryQuestionOnce() throws Exception {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                QuizActivity.class).putExtra("mode", 5);
        try (ActivityController<QuizActivity> controller =
                     Robolectric.buildActivity(QuizActivity.class, intent).setup()) {
            QuizActivity activity = controller.get();
            waitFor(activity, () -> {
                TextView title = activity.findViewById(R.id.tv_title);
                return title != null && title.getText().toString().contains("全部练习");
            });
            TextView title = activity.findViewById(R.id.tv_title);
            assertEquals("全部练习 · 1/3", title.getText().toString());
            android.widget.ProgressBar bar = activity.findViewById(R.id.progress_quiz);
            assertEquals(3, bar.getMax());
            int count = onWorker(() -> app.db().questionDao().count());
            assertEquals(3, count);
        }
    }

    @Test
    public void modeSwitchKeepsProgressAndSelection() throws Exception {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                QuizActivity.class).putExtra("mode", 5);
        try (ActivityController<QuizActivity> controller =
                     Robolectric.buildActivity(QuizActivity.class, intent).setup()) {
            QuizActivity activity = controller.get();
            waitFor(activity, () -> {
                TextView title = activity.findViewById(R.id.tv_title);
                return title != null && title.getText().toString().contains("1/3");
            });

            OptionGroupView options = activity.findViewById(R.id.options_container);
            assertEquals(4, options.getChildCount());
            options.getChildAt(0).performClick();
            assertEquals(1, options.getSelected().size());

            // 刷题 -> 背题
            activity.findViewById(R.id.btn_mode).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            View card = activity.findViewById(R.id.card_result);
            assertEquals(View.VISIBLE, card.getVisibility());
            assertTrue(((TextView) activity.findViewById(R.id.tv_result)).getText().toString()
                    .contains("背题模式"));
            assertTrue(((TextView) activity.findViewById(R.id.tv_analysis)).getText().toString()
                    .contains("第 0 题的解析"));
            assertEquals("下一题", ((TextView) activity.findViewById(R.id.btn_submit)).getText().toString());
            assertEquals(1, options.getSelected().size());

            // 背题 -> 刷题，题号与已选选项都保留
            activity.findViewById(R.id.btn_mode).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(View.GONE, card.getVisibility());
            assertEquals("提交答案", ((TextView) activity.findViewById(R.id.btn_submit)).getText().toString());
            assertEquals(1, options.getSelected().size());
            assertEquals("全部练习 · 1/3", ((TextView) activity.findViewById(R.id.tv_title)).getText().toString());

            // 背题模式不写入做题记录
            int records = onWorker(() -> app.db().practiceDao().totalCount());
            assertEquals(0, records);
        }
    }

    private void waitFor(QuizActivity activity, java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(30);
        }
        fail("timeout waiting for activity state");
    }
}
