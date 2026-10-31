package com.example.offlinequiz.ui;

import static org.junit.Assert.assertNotNull;

import android.content.Intent;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.example.offlinequiz.App;
import com.example.offlinequiz.R;
import com.example.offlinequiz.data.entity.Question;
import com.example.offlinequiz.ui.imports.ImportActivity;
import com.example.offlinequiz.ui.quiz.QuizActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Robolectric smoke test: inflates the shell, the import screen and the quiz screen on a
 * JVM, which catches broken layouts, missing resources and Room wiring without a device.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AppSmokeTest {

    @Test
    public void mainActivityInflatesShell() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            assertNotNull(activity.findViewById(R.id.bottom_nav));
            assertNotNull(activity.findViewById(R.id.fragment_container));
            assertNotNull(activity.findViewById(R.id.empty_state));
        }
    }

    @Test
    public void importActivityInflates() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                ImportActivity.class);
        try (ActivityController<ImportActivity> controller =
                     Robolectric.buildActivity(ImportActivity.class, intent).setup()) {
            assertNotNull(controller.get().findViewById(R.id.btn_pick_file));
        }
    }

    @Test
    public void quizActivityInflatesWithAQuestion() throws Exception {
        App app = ApplicationProvider.getApplicationContext();
        Question q = new Question();
        q.stem = "测试题干";
        q.type = "single";
        q.options = "[\"A. 甲\",\"B. 乙\"]";
        q.answer = "A";
        q.knowledge = "测试";
        q.contentHash = "hash-smoke-1";
        q.createdAt = System.currentTimeMillis();

        // Room refuses main-thread writes; insert from a worker like the app does.
        Thread worker = new Thread(() -> app.db().questionDao().insert(q));
        worker.start();
        worker.join();

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                QuizActivity.class)
                .putExtra("mode", 4)
                .putExtra("difficulty", 2)
                .putExtra("count", 1);
        try (ActivityController<QuizActivity> controller =
                     Robolectric.buildActivity(QuizActivity.class, intent).setup()) {
            assertNotNull(controller.get().findViewById(R.id.tv_stem));
            assertNotNull(controller.get().findViewById(R.id.btn_submit));
            View options = controller.get().findViewById(R.id.options_container);
            assertNotNull(options);
        }
    }
}
