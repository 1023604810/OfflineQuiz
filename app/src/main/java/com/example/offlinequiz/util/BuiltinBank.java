package com.example.offlinequiz.util;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;

/**
 * Optional bundled question bank.
 *
 * <p>The repository does not ship any question data; a developer can drop a bank at
 * {@code assets/questionbank/ict.json} locally (the folder is git-ignored). The UI asks
 * this helper before showing the "import bundled bank" entry points.</p>
 */
public final class BuiltinBank {

    public static final String ASSET = "questionbank/ict.json";

    private BuiltinBank() {
    }

    public static boolean available(Context context) {
        try (InputStream in = context.getAssets().open(ASSET)) {
            return in != null;
        } catch (IOException e) {
            return false;
        }
    }
}
