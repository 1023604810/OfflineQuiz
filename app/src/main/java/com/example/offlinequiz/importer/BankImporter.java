package com.example.offlinequiz.importer;

import android.os.SystemClock;

import com.example.offlinequiz.data.AppDatabase;
import com.example.offlinequiz.data.entity.Question;
import com.example.offlinequiz.importer.dto.RawQuestion;
import com.example.offlinequiz.util.HashUtil;
import com.google.gson.Gson;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Orchestrates one import: parse -> validate -> exact-match upsert (insert or overwrite)
 * inside a single transaction, then re-run difficulty calibration.
 *
 * <p>Questions are matched by their normalised stem ({@code stem_key}); an existing row
 * is overwritten with the new content while keeping its database id, so practice
 * records, wrong-book entries and favorites stay attached. An interrupted import leaves
 * no partial rows because the whole run is one transaction.</p>
 */
public class BankImporter {

    public interface Listener {
        void onProgress(int done, int total);

        void onFinished(ImportResult result);
    }

    private static final Set<String> TYPES =
            new HashSet<>(Arrays.asList("single", "multiple", "judge", "blank", "essay"));

    private final AppDatabase db;
    private final Gson gson = new Gson();

    public BankImporter(AppDatabase db) {
        this.db = db;
    }

    public ImportResult importStream(InputStream in, String fileName, String mimeType,
                                     Listener listener) throws Exception {
        QuestionParser parser = ImporterFactory.forFile(fileName, mimeType);
        ImportResult result = new ImportResult();
        List<RawQuestion> raws = new ArrayList<>();
        parser.parse(in, result, raws);
        result.parsed = raws.size();
        if (result.parsed == 0) {
            throw new IllegalArgumentException("没有解析到任何题目，请检查文件模板");
        }

        // make sure legacy rows have a stem key before the first upsert
        new com.example.offlinequiz.service.StemKeyBackfill(db).backfill();

        com.example.offlinequiz.service.QuestionUpsertService upsertService =
                new com.example.offlinequiz.service.QuestionUpsertService(db);
        long now = System.currentTimeMillis();

        db.runInTransaction(() -> {
            for (int i = 0; i < raws.size(); i++) {
                Question q = validate(raws.get(i), result, now);
                if (q != null) {
                    com.example.offlinequiz.service.QuestionUpsertService.Result outcome =
                            upsertService.upsert(q);
                    if (outcome.isUpdated()) {
                        result.updated++;
                    } else {
                        result.inserted++;
                    }
                }
                if (listener != null && (i + 1) % 100 == 0) {
                    listener.onProgress(i + 1, raws.size());
                }
            }
        });

        // an old bank file may still contain figure-only questions: remove them again
        new com.example.offlinequiz.service.FigurelessQuestionCleaner(
                com.example.offlinequiz.App.get(), db).clean();

        // classify difficulties and back-fill explanations for the whole bank
        new com.example.offlinequiz.service.DifficultyCalibrator(db).calibrate(true);
        if (listener != null) {
            listener.onProgress(raws.size(), raws.size());
            listener.onFinished(result);
        }
        return result;
    }

    /** Returns a ready-to-insert entity, or null after recording the failure. */
    private Question validate(RawQuestion raw, ImportResult result, long now) {
        String row = raw.sourceRow > 0 ? ("第 " + raw.sourceRow + " 行") : "某一行";
        String stem = raw.stem == null ? "" : raw.stem.trim();
        String answer = raw.answer == null ? "" : raw.answer.trim();
        if (stem.isEmpty()) {
            result.error(row + "：题干为空，已跳过");
            return null;
        }
        if (answer.isEmpty()) {
            result.error(row + "：缺少答案，已跳过");
            return null;
        }

        List<String> options = new ArrayList<>();
        if (raw.options != null) {
            for (String o : raw.options) {
                if (o != null && !o.trim().isEmpty()) {
                    options.add(normalizeOption(options.size(), o.trim()));
                }
            }
        }

        String type = normalizeType(raw.type, answer, options);
        if (type == null) {
            result.error(row + "：题型无法识别（" + raw.type + "），已跳过");
            return null;
        }
        if (("single".equals(type) || "multiple".equals(type)) && options.size() < 2) {
            result.error(row + "：选择题缺少选项，已跳过");
            return null;
        }
        if ("single".equals(type)) {
            String letters = optionLetters(options);
            String ans = normalizeLetters(answer);
            if (ans.length() != 1 || !letters.contains(ans)) {
                result.error(row + "：单选题答案与选项不匹配（答案 " + answer + "），已跳过");
                return null;
            }
            answer = ans;
        } else if ("multiple".equals(type)) {
            String letters = optionLetters(options);
            String ans = normalizeLetters(answer);
            if (ans.isEmpty() || !letters.contains(ans)) {
                result.error(row + "：多选题答案与选项不匹配（答案 " + answer + "），已跳过");
                return null;
            }
            answer = ans;
        }

        String knowledge = raw.knowledge == null ? "" : raw.knowledge.trim();
        if (knowledge.isEmpty()) {
            knowledge = "未分类";
        }
        int difficulty = raw.difficulty == null ? 2 : raw.difficulty;
        difficulty = Math.max(1, Math.min(3, difficulty));

        String optionsJson = options.isEmpty() ? null : gson.toJson(options);
        Question q = new Question();
        q.extId = raw.id;
        q.stem = stem;
        q.type = type;
        q.options = optionsJson;
        q.answer = answer;
        q.analysis = raw.analysis == null || raw.analysis.trim().isEmpty() ? null : raw.analysis.trim();
        q.knowledge = knowledge;
        q.difficulty = difficulty;
        q.contentHash = HashUtil.md5(stem + "|" + (optionsJson == null ? "" : optionsJson));
        q.createdAt = now;
        return q;
    }

    private static String normalizeType(String raw, String answer, List<String> options) {
        String type = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        switch (type) {
            case "single":
            case "single_choice":
            case "单选":
                return "single";
            case "multiple":
            case "multi":
            case "多选":
                return "multiple";
            case "judge":
            case "truefalse":
            case "判断":
                return "judge";
            case "blank":
            case "fill":
            case "填空":
                return "blank";
            case "essay":
            case "subjective":
            case "主观":
                return "essay";
            default:
                break;
        }
        // infer when the type column is missing
        if (options.size() >= 2) {
            return normalizeLetters(answer).length() > 1 ? "multiple" : "single";
        }
        String a = answer.trim().toUpperCase(Locale.ROOT);
        if (a.equals("T") || a.equals("F") || a.equals("TRUE") || a.equals("FALSE")
                || a.equals("对") || a.equals("错") || a.equals("正确") || a.equals("错误")) {
            return "judge";
        }
        if (answer.contains("|")) {
            return "blank";
        }
        return type.isEmpty() ? "essay" : null;
    }

    private static String normalizeOption(int index, String option) {
        if (!option.isEmpty() && option.charAt(0) >= 'A' && option.charAt(0) <= 'H'
                && option.length() > 1) {
            char sep = option.charAt(1);
            if (sep == '.' || sep == '．' || sep == '、' || sep == ' ' || sep == ':'
                    || sep == '：' || sep == ')') {
                return option;
            }
        }
        return (char) ('A' + index) + ". " + option;
    }

    private static String optionLetters(List<String> options) {
        StringBuilder sb = new StringBuilder();
        for (String o : options) {
            if (!o.isEmpty()) {
                sb.append(Character.toUpperCase(o.charAt(0)));
            }
        }
        return sb.toString();
    }

    private static String normalizeLetters(String raw) {
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toUpperCase(Locale.ROOT).toCharArray()) {
            if (c >= 'A' && c <= 'H' && sb.indexOf(String.valueOf(c)) < 0) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Convenience for the UI thread wrapper: measures elapsed time for the summary. */
    public static long nowForTiming() {
        return SystemClock.elapsedRealtime();
    }
}
