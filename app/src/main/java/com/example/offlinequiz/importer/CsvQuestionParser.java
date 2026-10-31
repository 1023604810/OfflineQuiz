package com.example.offlinequiz.importer;

import com.example.offlinequiz.importer.dto.RawQuestion;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CSV bank: header row + one question per row. Multi-line cells are supported when
 * quoted. Options inside one cell are separated by a newline (or "|" as a fallback).
 */
public class CsvQuestionParser implements QuestionParser {

    @Override
    public void parse(InputStream in, ImportResult result, List<RawQuestion> out) throws Exception {
        String text = IoUtil.decode(IoUtil.readAll(in));
        List<List<String>> rows = readRows(text);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("CSV 内容为空");
        }
        Map<String, Integer> header = mapHeader(rows.get(0));
        if (!header.containsKey("stem")) {
            throw new IllegalArgumentException("CSV 表头缺少 stem（题干）列");
        }
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (isBlankRow(row)) {
                continue;
            }
            RawQuestion raw = new RawQuestion();
            raw.sourceRow = i + 1;
            raw.id = cell(row, header, "id");
            raw.stem = cell(row, header, "stem");
            raw.type = cell(row, header, "type");
            raw.answer = cell(row, header, "answer");
            raw.analysis = cell(row, header, "analysis");
            raw.knowledge = cell(row, header, "knowledge");
            raw.options = splitOptions(cell(row, header, "options"));
            String difficulty = cell(row, header, "difficulty");
            if (difficulty != null && !difficulty.isEmpty()) {
                try {
                    raw.difficulty = (int) Double.parseDouble(difficulty);
                } catch (NumberFormatException ignored) {
                    // keep default
                }
            }
            out.add(raw);
        }
    }

    private static List<String> splitOptions(String cell) {
        List<String> options = new ArrayList<>();
        if (cell == null || cell.trim().isEmpty()) {
            return options;
        }
        String[] parts = cell.contains("\n") ? cell.split("\\r?\\n") : cell.split("\\|");
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) {
                options.add(t);
            }
        }
        return options;
    }

    private static String cell(List<String> row, Map<String, Integer> header, String key) {
        Integer index = header.get(key);
        if (index == null || index >= row.size()) {
            return null;
        }
        String value = row.get(index);
        return value == null ? null : value.trim();
    }

    private static boolean isBlankRow(List<String> row) {
        for (String s : row) {
            if (s != null && !s.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Integer> mapHeader(List<String> headerRow) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            String key = normalizeHeader(headerRow.get(i));
            if (key != null && !map.containsKey(key)) {
                map.put(key, i);
            }
        }
        return map;
    }

    private static String normalizeHeader(String raw) {
        if (raw == null) {
            return null;
        }
        String h = raw.trim().toLowerCase(Locale.ROOT).replace("\uFEFF", "");
        switch (h) {
            case "id":
            case "编号":
            case "题目编号":
                return "id";
            case "stem":
            case "question":
            case "题干":
            case "题目":
                return "stem";
            case "type":
            case "题型":
                return "type";
            case "options":
            case "option":
            case "选项":
                return "options";
            case "answer":
            case "答案":
            case "标准答案":
                return "answer";
            case "analysis":
            case "解析":
            case "答案解析":
                return "analysis";
            case "knowledge":
            case "knowledge_tag":
            case "知识点":
                return "knowledge";
            case "difficulty":
            case "难度":
                return "difficulty";
            default:
                return null;
        }
    }

    /** RFC4180-ish reader: quotes, doubled quotes and embedded newlines. */
    static List<List<String>> readRows(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < chars.length && chars[i + 1] == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    current.add(field.toString());
                    field.setLength(0);
                } else if (c == '\r') {
                    // handled together with \n
                } else if (c == '\n') {
                    current.add(field.toString());
                    field.setLength(0);
                    rows.add(current);
                    current = new ArrayList<>();
                } else {
                    field.append(c);
                }
            }
        }
        if (field.length() > 0 || !current.isEmpty()) {
            current.add(field.toString());
            rows.add(current);
        }
        return rows;
    }
}
