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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;

import java.util.concurrent.Callable;

/** 「返回上一题」：浏览历史、边界禁用、答案与判分结果回显。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class QuizNavigationTest {

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

    private void resetAndSeed(Question... questions) throws Exception {
        app = ApplicationProvider.getApplicationContext();
        onWorker(() -> {
            app.db().questionDao().deleteAll();
            app.db().practiceDao().deleteAll();
            app.db().wrongDao().deleteAll();
            long now = System.currentTimeMillis();
            for (Question q : questions) {
                q.createdAt = now;
                q.contentHash = "nav-" + q.stem;
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

    private static Question single(String stem, String answer) {
        Question q = new Question();
        q.stem = stem;
        q.type = "single";
        q.options = "[\"A. 甲\",\"B. 乙\",\"C. 丙\",\"D. 丁\"]";
        q.answer = answer;
        q.analysis = "解析：" + stem;
        q.knowledge = "测试";
        q.difficulty = 2;
        q.difficultyScore = 50;
        return q;
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

    private QuizActivity launchAll() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                QuizActivity.class).putExtra("mode", 5);
        return Robolectric.buildActivity(QuizActivity.class, intent).setup().get();
    }

    private void clickOption(QuizActivity activity, int childIndex) {
        OptionGroupView options = activity.findViewById(R.id.options_container);
        options.getChildAt(childIndex).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private String title(QuizActivity activity) {
        return ((TextView) activity.findViewById(R.id.tv_title)).getText().toString();
    }

    @Test
    public void backNavigationRestoresAnswersAndResultsInOrder() throws Exception {
        resetAndSeed(single("第一题", "A"), single("第二题", "B"), single("第三题", "C"));
        QuizActivity activity = launchAll();
        try {
            waitFor(activity, () -> title(activity).contains("1/3"));

            // first question: back button must be disabled
            View prev = activity.findViewById(R.id.btn_prev_question);
            assertFalse(prev.isEnabled());

            // answer Q1 correctly and submit
            clickOption(activity, 0);
            activity.findViewById(R.id.btn_submit).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(View.VISIBLE, activity.findViewById(R.id.card_result).getVisibility());
            assertTrue(((TextView) activity.findViewById(R.id.tv_result)).getText().toString()
                    .contains("回答正确"));

            // move to Q2 and Q3
            activity.findViewById(R.id.btn_submit).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            waitFor(activity, () -> title(activity).contains("2/3"));
            assertTrue(activity.findViewById(R.id.btn_prev_question).isEnabled());
            clickOption(activity, 1);
            activity.findViewById(R.id.btn_submit).performClick();
            activity.findViewById(R.id.btn_submit).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            waitFor(activity, () -> title(activity).contains("3/3"));

            // Q3: leave a draft (not submitted)
            clickOption(activity, 2);

            // back to Q2: submitted result + selected option restored
            activity.findViewById(R.id.btn_prev_question).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue(title(activity).contains("2/3"));
            assertEquals(View.VISIBLE, activity.findViewById(R.id.card_result).getVisibility());
            assertTrue(((TextView) activity.findViewById(R.id.tv_user_answer)).getText().toString()
                    .contains("B"));
            assertTrue(((OptionGroupView) activity.findViewById(R.id.options_container))
                    .getSelected().contains("B"));

            // back to Q1: first question, button disabled again, answer restored
            activity.findViewById(R.id.btn_prev_question).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue(title(activity).contains("1/3"));
            assertEquals(View.VISIBLE, activity.findViewById(R.id.card_result).getVisibility());
            assertTrue(((OptionGroupView) activity.findViewById(R.id.options_container))
                    .getSelected().contains("A"));
            assertFalse(activity.findViewById(R.id.btn_prev_question).isEnabled());

            // forward again: Q2 and Q3 keep their own state
            activity.findViewById(R.id.btn_submit).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue(title(activity).contains("2/3"));
            activity.findViewById(R.id.btn_submit).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue(title(activity).contains("3/3"));
            assertEquals(View.GONE, activity.findViewById(R.id.card_result).getVisibility());
            assertTrue(((OptionGroupView) activity.findViewById(R.id.options_container))
                    .getSelected().contains("C"));
        } finally {
            activity.finish();
        }
    }

    @Test
    public void judgeFalseCanBeSubmittedAndRestored() throws Exception {
        Question q = new Question();
        q.stem = "判断题：OSPF 是距离矢量协议";
        q.type = "judge";
        q.options = null;
        q.answer = "F";
        q.analysis = "OSPF 是链路状态协议。";
        q.knowledge = "测试";
        q.difficulty = 2;
        q.difficultyScore = 55;
        resetAndSeed(q);

        QuizActivity activity = launchAll();
        try {
            waitFor(activity, () -> title(activity).contains("1/1"));
            activity.findViewById(R.id.btn_judge_false).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            activity.findViewById(R.id.btn_submit).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(View.VISIBLE, activity.findViewById(R.id.card_result).getVisibility());
            assertTrue(((TextView) activity.findViewById(R.id.tv_result)).getText().toString()
                    .contains("回答正确"));
            assertEquals("你的答案：错误",
                    ((TextView) activity.findViewById(R.id.tv_user_answer)).getText().toString());
        } finally {
            activity.finish();
        }
    }
}
