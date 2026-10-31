package com.example.offlinequiz.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Exact-match key for "same question" detection.
 *
 * <p>Two stems are the same question when, after NFKC normalisation, lower-casing,
 * removing all whitespace and trimming trailing punctuation, the texts are equal.
 * This keeps "RSTP 协议存在几种端口状态：" and "RSTP协议存在几种端口状态" identical
 * while never fuzzy-merging different questions.</p>
 */
public final class QuestionKey {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final String TRAILING = "。．.？?！!；;：:，,";

    private QuestionKey() {
    }

    public static String of(String stem) {
        if (stem == null) {
            return "";
        }
        String text = Normalizer.normalize(stem, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        text = WHITESPACE.matcher(text).replaceAll("");
        int end = text.length();
        while (end > 0 && TRAILING.indexOf(text.charAt(end - 1)) >= 0) {
            end--;
        }
        return text.substring(0, end);
    }
}
