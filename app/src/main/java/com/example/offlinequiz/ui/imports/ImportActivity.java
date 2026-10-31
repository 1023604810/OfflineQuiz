package com.example.offlinequiz.ui.imports;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.offlinequiz.App;
import com.example.offlinequiz.databinding.ActivityImportBinding;
import com.example.offlinequiz.importer.BankImporter;
import com.example.offlinequiz.importer.ImportResult;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** SAF-based bank import plus the optional bundled-bank shortcut. */
public class ImportActivity extends AppCompatActivity {

    public static final String EXTRA_BUILTIN = "builtin";
    private static final String BUILTIN_ASSET =
            com.example.offlinequiz.util.BuiltinBank.ASSET;

    private ActivityImportBinding b;
    private boolean running;

    private final ActivityResultLauncher<String[]> filePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    importFromUri(uri);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityImportBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.btnBack.setOnClickListener(v -> finish());
        b.btnPickFile.setOnClickListener(v -> {
            if (!running) {
                filePicker.launch(new String[]{
                        "application/json",
                        "text/csv",
                        "text/comma-separated-values",
                        "text/plain",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                });
            }
        });
        b.btnImportBuiltin.setOnClickListener(v -> {
            if (!running) {
                importBuiltin();
            }
        });

        boolean builtinAvailable = com.example.offlinequiz.util.BuiltinBank.available(this);
        b.btnImportBuiltin.setVisibility(builtinAvailable ? View.VISIBLE : View.GONE);

        if (getIntent().getBooleanExtra(EXTRA_BUILTIN, false)) {
            if (builtinAvailable) {
                importBuiltin();
            } else {
                showError("当前版本未内置题库，请使用「选择本地文件」导入");
            }
        }
    }

    private void importFromUri(Uri uri) {
        String name = displayName(uri);
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) {
                showError("无法读取所选文件");
                return;
            }
            runImport(in, name, getContentResolver().getType(uri));
        } catch (Exception e) {
            showError("无法打开文件：" + e.getMessage());
        }
    }

    private void importBuiltin() {
        try {
            InputStream in = getAssets().open(BUILTIN_ASSET);
            runImport(in, "builtin-bank.json", "application/json");
        } catch (IOException e) {
            showError("内置题库缺失：" + e.getMessage());
        }
    }

    private void runImport(InputStream in, String name, String mime) {
        running = true;
        b.progressImport.setVisibility(View.VISIBLE);
        b.progressImport.setIndeterminate(true);
        b.tvImportStatus.setVisibility(View.VISIBLE);
        b.tvImportStatus.setText("正在解析 " + name + " …");
        b.cardResult.setVisibility(View.GONE);
        b.btnPickFile.setEnabled(false);
        b.btnImportBuiltin.setEnabled(false);

        final String fileName = name;
        App.get().io().execute(() -> {
            try {
                BankImporter importer = new BankImporter(App.get().db());
                ImportResult result = importer.importStream(in, fileName, mime,
                        new BankImporter.Listener() {
                            @Override
                            public void onProgress(int done, int total) {
                                App.get().runOnUiThread(() -> {
                                    b.progressImport.setIndeterminate(false);
                                    b.progressImport.setMax(Math.max(total, 1));
                                    b.progressImport.setProgress(done);
                                    b.tvImportStatus.setText("已处理 " + done + " / " + total + " 条");
                                });
                            }

                            @Override
                            public void onFinished(ImportResult r) {
                                // summary shown from the caller thread below
                            }
                        });
                in.close();
                final int bankTotal = App.get().db().questionDao().count();
                App.get().runOnUiThread(() -> showResult(fileName, result, bankTotal));
            } catch (Exception e) {
                App.get().runOnUiThread(() -> showError(messageOf(e)));
            }
        });
    }

    private void showResult(String fileName, ImportResult result, int bankTotal) {
        running = false;
        b.progressImport.setVisibility(View.GONE);
        b.tvImportStatus.setVisibility(View.GONE);
        b.btnPickFile.setEnabled(true);
        b.btnImportBuiltin.setEnabled(true);
        b.cardResult.setVisibility(View.VISIBLE);

        String title;
        if (result.inserted > 0 && result.updated > 0) {
            title = "导入完成：新增 " + result.inserted + " 题，覆盖 " + result.updated + " 题";
        } else if (result.inserted > 0) {
            title = "导入完成：新增 " + result.inserted + " 题";
        } else if (result.updated > 0) {
            title = "题库已更新：覆盖 " + result.updated + " 题";
        } else {
            title = "没有可导入的题目";
        }
        b.tvResultTitle.setText(title);
        b.tvResultTitle.setTextColor(getColor(com.example.offlinequiz.R.color.text_primary));
        b.tvResultDetail.setText(fileName + "\n" + result.summary()
                + "\n题库现有 " + bankTotal + " 题");

        List<String> errors = result.errors;
        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder("失败明细：\n");
            for (String e : errors) {
                sb.append("· ").append(e).append('\n');
            }
            if (result.failed > errors.size()) {
                sb.append("（其余 ").append(result.failed - errors.size()).append(" 条未展示）");
            }
            b.tvResultErrors.setText(sb.toString());
            b.tvResultErrors.setVisibility(View.VISIBLE);
        } else {
            b.tvResultErrors.setVisibility(View.GONE);
        }
    }

    private void showError(String message) {
        running = false;
        b.progressImport.setVisibility(View.GONE);
        b.tvImportStatus.setVisibility(View.GONE);
        b.btnPickFile.setEnabled(true);
        b.btnImportBuiltin.setEnabled(true);
        b.cardResult.setVisibility(View.VISIBLE);
        b.tvResultTitle.setText("导入失败");
        b.tvResultTitle.setTextColor(getColor(com.example.offlinequiz.R.color.danger));
        b.tvResultDetail.setText(message);
        b.tvResultErrors.setVisibility(View.GONE);
    }

    private String displayName(Uri uri) {
        String name = null;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    name = cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
            // fall back to the path segment
        }
        if (name == null) {
            name = uri.getLastPathSegment() == null ? "题库文件" : uri.getLastPathSegment();
        }
        return name;
    }

    private static String messageOf(Exception e) {
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? "解析失败，请检查文件格式" : m;
    }
}
