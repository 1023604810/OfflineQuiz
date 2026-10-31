package com.example.offlinequiz.importer;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of one import run.
 *
 * <p>{@link #inserted} counts questions that did not exist in the bank,
 * {@link #updated} counts existing questions (same normalised stem) that were
 * overwritten by this run.</p>
 */
public class ImportResult {

    public int parsed;
    public int inserted;
    public int updated;
    public int failed;

    public final List<String> errors = new ArrayList<>();

    public void error(String message) {
        failed++;
        if (errors.size() < 50) {
            errors.add(message);
        }
    }

    public String summary() {
        return "新增 " + inserted + " 题，覆盖更新 " + updated + " 题，失败 " + failed + " 题";
    }
}
