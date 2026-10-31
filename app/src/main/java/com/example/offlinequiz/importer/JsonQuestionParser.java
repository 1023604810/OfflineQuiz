package com.example.offlinequiz.importer;

import com.example.offlinequiz.importer.dto.RawQuestion;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.util.List;

/**
 * JSON bank: either {"questions": [...]} (with optional subject/version) or a bare
 * array of question objects.
 */
public class JsonQuestionParser implements QuestionParser {

    private final Gson gson = new Gson();

    @Override
    public void parse(InputStream in, ImportResult result, List<RawQuestion> out) throws Exception {
        String text = IoUtil.decode(IoUtil.readAll(in)).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("文件内容为空");
        }
        JsonElement root = JsonParser.parseString(text);
        JsonArray array;
        if (root.isJsonArray()) {
            array = root.getAsJsonArray();
        } else if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            JsonElement questions = obj.get("questions");
            if (questions == null || !questions.isJsonArray()) {
                throw new IllegalArgumentException("JSON 缺少 questions 数组");
            }
            array = questions.getAsJsonArray();
        } else {
            throw new IllegalArgumentException("不是合法的 JSON 题库");
        }

        for (int i = 0; i < array.size(); i++) {
            JsonElement el = array.get(i);
            if (!el.isJsonObject()) {
                result.error("第 " + (i + 1) + " 条：不是对象，已跳过");
                continue;
            }
            RawQuestion raw = gson.fromJson(el, RawQuestion.class);
            raw.sourceRow = i + 1;
            out.add(raw);
        }
    }
}
