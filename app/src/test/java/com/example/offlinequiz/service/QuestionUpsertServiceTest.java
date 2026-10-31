package com.example.offlinequiz.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;

import com.example.offlinequiz.App;
import com.example.offlinequiz.data.entity.PracticeRecord;
import com.example.offlinequiz.data.entity.Question;
import com.example.offlinequiz.importer.BankImporter;
import com.example.offlinequiz.importer.ImportResult;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.SQLiteMode;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

/** Upsert semantics: insert when absent, overwrite in place when the stem matches. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
public class QuestionUpsertServiceTest {

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

    private void clear() throws Exception {
        app = ApplicationProvider.getApplicationContext();
        onWorker(() -> {
            app.db().practiceDao().deleteAll();
            app.db().wrongDao().deleteAll();
            app.db().favoriteDao().deleteAll();
            app.db().questionDao().deleteAll();
            return null;
        });
    }

    private static Question question(String stem, String answer, String analysis) {
        Question q = new Question();
        q.stem = stem;
        q.type = "single";
        q.options = "[\"A. 甲\",\"B. 乙\",\"C. 丙\",\"D. 丁\"]";
        q.answer = answer;
        q.analysis = analysis;
        q.knowledge = "测试";
        q.difficulty = 2;
        q.contentHash = "hash-" + stem;
        q.createdAt = System.currentTimeMillis();
        return q;
    }

    @Test
    public void insertThenOverwriteKeepsSingleRow() throws Exception {
        clear();
        QuestionUpsertService service = new QuestionUpsertService(app.db());

        Question first = question("VRP 中修改设备名称的命令是？", "A", "旧解析");
        QuestionUpsertService.Result inserted = onWorker(() -> service.upsert(first));
        assertEquals(QuestionUpsertService.Outcome.INSERTED, inserted.outcome);

        Question second = question("VRP 中修改设备名称的命令是？", "B", "新解析：使用 sysname");
        QuestionUpsertService.Result updated = onWorker(() -> service.upsert(second));
        assertEquals(QuestionUpsertService.Outcome.UPDATED, updated.outcome);
        assertEquals(inserted.questionId, updated.questionId);

        Question stored = onWorker(() -> app.db().questionDao().getById(inserted.questionId));
        assertEquals("新解析：使用 sysname", stored.analysis);
        assertEquals("B", stored.answer);
        assertEquals(1, (int) onWorker(() -> app.db().questionDao().count()));
    }

    @Test
    public void differentSpacingAndPunctuationMatchTheSameQuestion() throws Exception {
        clear();
        QuestionUpsertService service = new QuestionUpsertService(app.db());

        QuestionUpsertService.Result a = onWorker(() ->
                service.upsert(question("RSTP 协议存在几种端口状态？", "A", "旧")));
        QuestionUpsertService.Result b = onWorker(() ->
                service.upsert(question("rstp协议存在几种端口状态", "A", "新")));

        assertEquals(QuestionUpsertService.Outcome.INSERTED, a.outcome);
        assertEquals(QuestionUpsertService.Outcome.UPDATED, b.outcome);
        assertEquals(1, (int) onWorker(() -> app.db().questionDao().count()));
    }

    @Test
    public void overwriteKeepsAttachedPracticeRecords() throws Exception {
        clear();
        QuestionUpsertService service = new QuestionUpsertService(app.db());
        QuestionUpsertService.Result inserted = onWorker(() ->
                service.upsert(question("OSPF 缺省 Hello 时间是多少？", "A", "旧")));

        onWorker(() -> {
            app.db().practiceDao().insert(new PracticeRecord(inserted.questionId, "A", 0, 3,
                    System.currentTimeMillis()));
            return null;
        });

        onWorker(() -> service.upsert(question("OSPF 缺省 Hello 时间是多少？", "B", "新")));

        assertEquals(1, (int) onWorker(() -> app.db().practiceDao().totalCount()));
        assertEquals(1, (int) onWorker(() -> app.db().questionDao().count()));
        assertEquals(1, (int) onWorker(() -> app.db().questionDao().countDistinctStemKeys()));
    }

    @Test
    public void importTwiceKeepsOnlyTheLatestVersion() throws Exception {
        clear();
        String v1 = "{\"questions\":["
                + "{\"stem\":\"测试新增题 A\",\"type\":\"single\",\"options\":[\"A. 1\",\"B. 2\"],\"answer\":\"A\",\"analysis\":\"旧解析 A\"},"
                + "{\"stem\":\"测试新增题 B\",\"type\":\"single\",\"options\":[\"A. 1\",\"B. 2\"],\"answer\":\"B\",\"analysis\":\"解析 B\"}"
                + "]}";
        String v2 = "{\"questions\":["
                + "{\"stem\":\"测试新增题 A\",\"type\":\"single\",\"options\":[\"A. 1\",\"B. 2\"],\"answer\":\"B\",\"analysis\":\"最新解析 A\"},"
                + "{\"stem\":\"测试新增题 C\",\"type\":\"single\",\"options\":[\"A. 1\",\"B. 2\"],\"answer\":\"A\",\"analysis\":\"解析 C\"}"
                + "]}";

        ImportResult first = onWorker(() -> new BankImporter(app.db()).importStream(
                new ByteArrayInputStream(v1.getBytes(StandardCharsets.UTF_8)),
                "v1.json", "application/json", null));
        assertEquals(2, first.inserted);
        assertEquals(0, first.updated);

        ImportResult second = onWorker(() -> new BankImporter(app.db()).importStream(
                new ByteArrayInputStream(v2.getBytes(StandardCharsets.UTF_8)),
                "v2.json", "application/json", null));
        assertEquals(1, second.inserted);
        assertEquals(1, second.updated);

        int count = onWorker(() -> app.db().questionDao().count());
        int distinct = onWorker(() -> app.db().questionDao().countDistinctStemKeys());
        assertEquals(3, count);
        assertEquals(3, distinct);

        Question a = onWorker(() -> app.db().questionDao().findByStemKey(
                com.example.offlinequiz.util.QuestionKey.of("测试新增题 A")));
        assertEquals("最新解析 A", a.analysis);
        assertEquals("B", a.answer);
        assertNotEquals(0, a.id);
        assertTrue(a.stemKey != null && !a.stemKey.isEmpty());
    }
}
