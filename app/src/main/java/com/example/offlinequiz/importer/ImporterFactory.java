package com.example.offlinequiz.importer;

import java.util.Locale;

/** Picks a parser from the picked file's name / mime type. */
public final class ImporterFactory {

    private ImporterFactory() {
    }

    public static QuestionParser forFile(String fileName, String mimeType) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".json")) {
            return new JsonQuestionParser();
        }
        if (name.endsWith(".csv") || name.endsWith(".txt")) {
            return new CsvQuestionParser();
        }
        if (name.endsWith(".xlsx")) {
            return new XlsxQuestionParser();
        }
        if (name.endsWith(".xls")) {
            throw new IllegalArgumentException("暂不支持旧版 .xls，请用 Excel 另存为 .xlsx 后再导入");
        }
        if (mimeType != null) {
            if (mimeType.contains("json")) {
                return new JsonQuestionParser();
            }
            if (mimeType.contains("csv") || mimeType.startsWith("text/")) {
                return new CsvQuestionParser();
            }
            if (mimeType.contains("spreadsheetml")) {
                return new XlsxQuestionParser();
            }
        }
        throw new IllegalArgumentException("文件格式不正确，请使用标准 JSON / CSV / Excel 模板");
    }

    public static String[] mimeTypes() {
        return new String[]{
                "application/json",
                "text/csv",
                "text/comma-separated-values",
                "text/plain",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "*/*"
        };
    }
}
