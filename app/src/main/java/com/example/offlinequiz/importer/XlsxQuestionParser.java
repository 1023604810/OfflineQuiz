package com.example.offlinequiz.importer;

import android.util.Xml;

import com.example.offlinequiz.importer.dto.RawQuestion;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Minimal streaming .xlsx reader: unzips the package, reads sharedStrings.xml and the
 * first worksheet. Avoids the ~8 MB that Apache POI would add to the APK while still
 * handling the usual "header row + data rows" bank layout.
 */
public class XlsxQuestionParser implements QuestionParser {

    private final CsvQuestionParser headerMapper = new CsvQuestionParser();

    @Override
    public void parse(InputStream in, ImportResult result, List<RawQuestion> out) throws Exception {
        byte[] bytes = IoUtil.readAll(in);
        Map<String, byte[]> parts = unzip(bytes);

        List<String> sharedStrings = new ArrayList<>();
        byte[] shared = parts.get("xl/sharedStrings.xml");
        if (shared != null) {
            sharedStrings = readSharedStrings(shared);
        }

        String sheetPath = "xl/worksheets/sheet1.xml";
        if (!parts.containsKey(sheetPath)) {
            for (String name : parts.keySet()) {
                if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
                    sheetPath = name;
                    break;
                }
            }
        }
        byte[] sheet = parts.get(sheetPath);
        if (sheet == null) {
            throw new IllegalArgumentException("Excel 文件里没有找到工作表");
        }

        List<SheetRow> rows = readRows(sheet, sharedStrings);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("工作表内容为空");
        }

        Map<String, Integer> header = new HashMap<>();
        SheetRow headerRow = rows.get(0);
        for (int i = 0; i < headerRow.cells.size(); i++) {
            String key = ColumnMapper.normalize(headerRow.cells.get(i));
            if (key != null && !header.containsKey(key)) {
                header.put(key, i);
            }
        }
        if (!header.containsKey("stem")) {
            throw new IllegalArgumentException("Excel 表头缺少 stem（题干）列");
        }
        for (int i = 1; i < rows.size(); i++) {
            SheetRow row = rows.get(i);
            if (row.cells.isEmpty()) {
                continue;
            }
            RawQuestion raw = new RawQuestion();
            raw.sourceRow = row.rowNumber;
            raw.id = row.cell(header.get("id"));
            raw.stem = row.cell(header.get("stem"));
            raw.type = row.cell(header.get("type"));
            raw.answer = row.cell(header.get("answer"));
            raw.analysis = row.cell(header.get("analysis"));
            raw.knowledge = row.cell(header.get("knowledge"));
            raw.options = splitOptions(row.cell(header.get("options")));
            String difficulty = row.cell(header.get("difficulty"));
            if (difficulty != null && !difficulty.trim().isEmpty()) {
                try {
                    raw.difficulty = (int) Double.parseDouble(difficulty.trim());
                } catch (NumberFormatException ignored) {
                    // default difficulty
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

    private static Map<String, byte[]> unzip(byte[] bytes) throws Exception {
        Map<String, byte[]> parts = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buf = new byte[16384];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                int n;
                while ((n = zis.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                String name = entry.getName();
                if (name.startsWith("xl/")) {
                    parts.put(name, out.toByteArray());
                }
            }
        }
        return parts;
    }

    private static List<String> readSharedStrings(byte[] xml) throws Exception {
        List<String> list = new ArrayList<>();
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new ByteArrayInputStream(xml), "UTF-8");
        int event = parser.getEventType();
        StringBuilder current = null;
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                if ("si".equals(parser.getName())) {
                    current = new StringBuilder();
                } else if ("t".equals(parser.getName()) && current != null) {
                    current.append(parser.nextText());
                    event = parser.getEventType();
                    continue;
                }
            } else if (event == XmlPullParser.END_TAG && "si".equals(parser.getName())) {
                list.add(current == null ? "" : current.toString());
                current = null;
            }
            event = parser.next();
        }
        return list;
    }

    private static List<SheetRow> readRows(byte[] xml, List<String> sharedStrings) throws Exception {
        List<SheetRow> rows = new ArrayList<>();
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new ByteArrayInputStream(xml), "UTF-8");
        int event = parser.getEventType();
        SheetRow currentRow = null;
        int column = 0;
        String cellType = null;
        String cellValue = null;
        StringBuilder inline = null;

        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String name = parser.getName();
                if ("row".equals(name)) {
                    currentRow = new SheetRow();
                    String r = parser.getAttributeValue(null, "r");
                    currentRow.rowNumber = r == null ? rows.size() + 1 : parseInt(r, rows.size() + 1);
                    currentRow.cells = new TreeMap<>();
                    column = 0;
                } else if ("c".equals(name)) {
                    String ref = parser.getAttributeValue(null, "r");
                    column = ref == null ? column : columnIndex(ref);
                    cellType = parser.getAttributeValue(null, "t");
                    cellValue = null;
                    inline = null;
                } else if ("v".equals(name)) {
                    cellValue = parser.nextText();
                    event = parser.getEventType();
                    continue;
                } else if ("t".equals(name) && cellType != null && "inlineStr".equals(cellType)) {
                    if (inline == null) {
                        inline = new StringBuilder();
                    }
                    inline.append(parser.nextText());
                    event = parser.getEventType();
                    continue;
                }
            } else if (event == XmlPullParser.END_TAG) {
                String name = parser.getName();
                if ("c".equals(name) && currentRow != null) {
                    String text = "";
                    if ("inlineStr".equals(cellType) && inline != null) {
                        text = inline.toString();
                    } else if (cellValue != null) {
                        if ("s".equals(cellType)) {
                            int idx = parseInt(cellValue, -1);
                            text = idx >= 0 && idx < sharedStrings.size() ? sharedStrings.get(idx) : "";
                        } else {
                            text = cellValue;
                        }
                    }
                    currentRow.cells.put(column, text);
                } else if ("row".equals(name) && currentRow != null) {
                    rows.add(currentRow);
                    currentRow = null;
                }
            }
            event = parser.next();
        }
        return rows;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    /** "AB12" -> 27 (zero based). */
    private static int columnIndex(String cellRef) {
        int col = 0;
        for (int i = 0; i < cellRef.length(); i++) {
            char c = cellRef.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                col = col * 26 + (c - 'A' + 1);
            } else if (c >= 'a' && c <= 'z') {
                col = col * 26 + (c - 'a' + 1);
            } else {
                break;
            }
        }
        return Math.max(0, col - 1);
    }

    private static class SheetRow {
        int rowNumber;
        TreeMap<Integer, String> cells = new TreeMap<>();

        String cell(Integer index) {
            if (index == null) {
                return null;
            }
            String v = cells.get(index);
            return v == null ? null : v.trim();
        }
    }

    /** Header aliases shared with the CSV parser. */
    static final class ColumnMapper {
        private ColumnMapper() {
        }

        static String normalize(String raw) {
            if (raw == null) {
                return null;
            }
            String h = raw.trim().toLowerCase(java.util.Locale.ROOT).replace("\uFEFF", "");
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
    }
}
