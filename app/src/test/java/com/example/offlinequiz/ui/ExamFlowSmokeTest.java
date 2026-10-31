package com.example.offlinequiz.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import com.example.offlinequiz.App;
import com.example.offlinequiz.R;
import com.example.offlinequiz.data.entity.ExamSession;
import com.example.offlinequiz.data.entity.Question;
import com.example.offlinequiz.service.ExamConfig;
import com.example.offlinequiz.service.ExamService;
import com.example.offlinequiz.ui.exam.ExamActivity;
import com.example.offlinequiz.ui.exam.ExamResultActivity;
import com.example.offlinequiz.ui.exam.ExamSetupActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;

import java.util.Map;

/** Smoke coverage for the exam flow: setup screen, timed paper, result page. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class ExamFlowSmokeTest {

    private static void seedSingleChoice(App app, String hash) {
        Question q = new Question();
        q.stem = "OSPF 使用哪种报文发现邻居？";
        q.type = "single";
        q.options = "[\"A. HELLO\",\"B. LSU\",\"C. DD\"]";
        q.answer = "A";
        q.knowledge = "路由协议/OSPF";
        q.contentHash = hash;
        q.createdAt = System.currentTimeMillis();
        app.db().questionDao().insert(q);
    }

    @Test
    public void examSetupActivityInflates() {
        try (ActivityController<ExamSetupActivity> controller =
                     Robolectric.buildActivity(ExamSetupActivity.class).setup()) {
            assertNotNull(controller.get().findViewById(R.id.btn_build_paper));
            assertNotNull(controller.get().findViewById(R.id.spinner_knowledge));
        }
    }

    @Test
    public void examActivityAndResultInflate() throws Exception {
        App app = ApplicationProvider.getApplicationContext();
        final ExamSession[] holder = new ExamSession[1];
        final Throwable[] error = new Throwable[1];

        Thread worker = new Thread(() -> {
            try {
                seedSingleChoice(app, "exam-hash-1");
                seedSingleChoice(app, "exam-hash-2");
                ExamConfig cfg = new ExamConfig();
                cfg.counts.put("single", 2);
                cfg.durationMinutes = 30;
                ExamService service = new ExamService(app.db().questionDao(), app.db().examDao());
                holder[0] = service.buildPaper(cfg);
            } catch (Throwable t) {
                error[0] = t;
            }
        });
        worker.start();
        worker.join();
        if (error[0] != null) {
            throw new AssertionError(error[0]);
        }

        ExamSession session = holder[0];
        assertNotNull(session);
        assertEquals(2, ExamService.questionCount(session));

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                ExamActivity.class).putExtra("session_id", session.id);
        try (ActivityController<ExamActivity> controller =
                     Robolectric.buildActivity(ExamActivity.class, intent).setup()) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNotNull(controller.get().findViewById(R.id.tv_timer));
            assertNotNull(controller.get().findViewById(R.id.btn_submit_exam));
            assertNotNull(controller.get().findViewById(R.id.options_container));
        }

        // grade the paper directly, then open the result page
        Thread submitter = new Thread(() -> {
            ExamService service = new ExamService(app.db().questionDao(), app.db().examDao());
            ExamSession fresh = app.db().examDao().getById(session.id);
            Map<Long, String> answers = ExamService.parseAnswers(fresh);
            for (long id : app.db().questionDao().allIds()) {
                answers.put(id, "A");
            }
            service.submit(fresh, answers);
        });
        submitter.start();
        submitter.join();

        Intent resultIntent = new Intent(ApplicationProvider.getApplicationContext(),
                ExamResultActivity.class).putExtra("session_id", session.id);
        try (ActivityController<ExamResultActivity> controller =
                     Robolectric.buildActivity(ExamResultActivity.class, resultIntent).setup()) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNotNull(controller.get().findViewById(R.id.tv_score));
            assertNotNull(controller.get().findViewById(R.id.ll_by_type));
        }
    }

    @Test
    public void tabsSwitchWithoutCrash() {
        try (ActivityController<MainActivity> controller =
                     Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            int[] tabs = {R.id.tab_exam, R.id.tab_wrong, R.id.tab_favorite, R.id.tab_stats};
            for (int tab : tabs) {
                activity.findViewById(R.id.bottom_nav);
                com.google.android.material.bottomnavigation.BottomNavigationView nav =
                        activity.findViewById(R.id.bottom_nav);
                nav.setSelectedItemId(tab);
                Shadows.shadowOf(Looper.getMainLooper()).idle();
            }
            assertTrue(activity.findViewById(R.id.fragment_container) != null);
        }
    }
}
