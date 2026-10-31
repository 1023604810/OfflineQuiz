package com.example.offlinequiz.ui.exam;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.offlinequiz.App;
import com.example.offlinequiz.R;
import com.example.offlinequiz.data.entity.ExamSession;
import com.example.offlinequiz.data.entity.Question;
import com.example.offlinequiz.databinding.ActivityExamBinding;
import com.example.offlinequiz.databinding.DialogAnswerSheetBinding;
import com.example.offlinequiz.engine.ExamResult;
import com.example.offlinequiz.service.ExamService;
import com.example.offlinequiz.util.QuestionText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Timed mock exam: one question per screen, answer sheet navigation, auto submit at 0. */
public class ExamActivity extends AppCompatActivity {

    private static final String EXTRA_SESSION_ID = "session_id";

    private ActivityExamBinding b;
    private ExamSession session;
    private ExamService service;
    private List<Question> questions = new ArrayList<>();
    private Map<Long, String> answers;
    private int index;
    private int judgeAnswer;
    private boolean submitting;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateTimer();
            handler.postDelayed(this, 1000);
        }
    };

    public static void start(Context context, long sessionId) {
        Intent intent = new Intent(context, ExamActivity.class);
        intent.putExtra(EXTRA_SESSION_ID, sessionId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityExamBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        service = new ExamService(App.get().db().questionDao(), App.get().db().examDao());
        long sessionId = getIntent().getLongExtra(EXTRA_SESSION_ID, -1);

        b.btnBack.setOnClickListener(v -> confirmExit());
        b.btnPrev.setOnClickListener(v -> move(-1));
        b.btnNext.setOnClickListener(v -> move(1));
        b.btnSubmitExam.setOnClickListener(v -> confirmSubmit());
        b.btnAnswerSheet.setOnClickListener(v -> showAnswerSheet());

        App.get().io().execute(() -> {
            ExamSession loaded = App.get().db().examDao().getById(sessionId);
            if (loaded == null) {
                App.get().runOnUiThread(this::finish);
                return;
            }
            if (loaded.status == 1) {
                App.get().runOnUiThread(() -> {
                    ExamResultActivity.start(this, loaded.id);
                    finish();
                });
                return;
            }
            List<Question> paper = service.loadPaper(loaded);
            Map<Long, String> saved = ExamService.parseAnswers(loaded);
            App.get().runOnUiThread(() -> {
                session = loaded;
                questions = paper;
                answers = saved;
                b.tvExamTitle.setText(loaded.title);
                index = 0;
                render();
                handler.post(tick);
            });
        });
    }

    private void updateTimer() {
        if (session == null) {
            return;
        }
        long end = session.startTime + session.timeLimit * 60_000L;
        long remain = end - System.currentTimeMillis();
        if (remain <= 0) {
            b.tvTimer.setText("00:00");
            autoSubmit();
            return;
        }
        long sec = remain / 1000;
        b.tvTimer.setText(String.format(java.util.Locale.US, "%02d:%02d", sec / 60, sec % 60));
    }

    private void render() {
        if (questions.isEmpty()) {
            return;
        }
        Question q = questions.get(index);
        judgeAnswer = 0;

        b.tvIndex.setText("第 " + (index + 1) + " / " + questions.size() + " 题");
        b.tvType.setText(QuestionText.typeLabel(q.type));
        b.tvScoreHint.setText(trim(ExamService.fullScore(q)) + " 分");
        b.tvStem.setText(q.stem);

        b.optionsContainer.setVisibility(View.GONE);
        b.judgeContainer.setVisibility(View.GONE);
        b.blanksContainer.setVisibility(View.GONE);
        b.etEssay.setVisibility(View.GONE);
        resetJudge();

        String saved = answers.get(q.id);
        String type = q.type == null ? "single" : q.type;
        switch (type) {
            case "multiple":
            case "single": {
                b.optionsContainer.setVisibility(View.VISIBLE);
                b.optionsContainer.setMultiple("multiple".equals(type));
                b.optionsContainer.setOptions(QuestionText.optionList(q));
                b.optionsContainer.setLocked(false);
                b.optionsContainer.clearSelection();
                if (saved != null) {
                    b.optionsContainer.setSelected(lettersOf(saved));
                }
                break;
            }
            case "judge":
                b.judgeContainer.setVisibility(View.VISIBLE);
                if (saved != null) {
                    selectJudge("T".equals(saved) ? 1 : 0);
                }
                break;
            case "blank":
                showBlanks(q, saved);
                break;
            case "essay":
                b.etEssay.setVisibility(View.VISIBLE);
                b.etEssay.setText(saved == null ? "" : saved);
                break;
            default:
                break;
        }

        b.progressExam.setMax(questions.size());
        b.progressExam.setProgress(index + 1);
        b.btnPrev.setEnabled(index > 0);
        b.btnNext.setText(index + 1 >= questions.size() ? "交卷" : "下一题");
        b.scrollExam.scrollTo(0, 0);
    }

    private void showBlanks(Question q, String saved) {
        b.blanksContainer.setVisibility(View.VISIBLE);
        b.blanksContainer.removeAllViews();
        String[] parts = saved == null ? new String[0] : saved.split("\\|", -1);
        int count = QuestionText.blankCount(q);
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < count; i++) {
            EditText et = new EditText(this);
            et.setHint("第 " + (i + 1) + " 空");
            et.setBackgroundResource(R.drawable.bg_option);
            et.setPadding((int) (14 * density), (int) (12 * density),
                    (int) (14 * density), (int) (12 * density));
            et.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            et.setHintTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            et.setTextSize(15);
            if (i < parts.length) {
                et.setText(parts[i]);
            }
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = i == count - 1 ? 0 : (int) (10 * density);
            et.setLayoutParams(lp);
            b.blanksContainer.addView(et);
        }
    }

    private void selectJudge(int value) {
        judgeAnswer = value;
        boolean yes = value == 1;
        b.btnJudgeTrue.setStrokeColorResource(yes ? R.color.brand : R.color.divider);
        b.btnJudgeTrue.setTextColor(ContextCompat.getColor(this,
                yes ? R.color.brand : R.color.text_primary));
        b.btnJudgeFalse.setStrokeColorResource(!yes ? R.color.brand : R.color.divider);
        b.btnJudgeFalse.setTextColor(ContextCompat.getColor(this,
                !yes ? R.color.brand : R.color.text_primary));
    }

    private void resetJudge() {
        b.btnJudgeTrue.setOnClickListener(v -> selectJudge(1));
        b.btnJudgeFalse.setOnClickListener(v -> selectJudge(0));
        b.btnJudgeTrue.setStrokeColorResource(R.color.divider);
        b.btnJudgeFalse.setStrokeColorResource(R.color.divider);
        b.btnJudgeTrue.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        b.btnJudgeFalse.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
    }

    private void captureCurrent() {
        if (questions.isEmpty() || answers == null) {
            return;
        }
        Question q = questions.get(index);
        String type = q.type == null ? "single" : q.type;
        String value = null;
        switch (type) {
            case "single":
            case "multiple": {
                Set<String> letters = b.optionsContainer.getSelected();
                if (!letters.isEmpty()) {
                    List<String> sorted = new ArrayList<>(letters);
                    java.util.Collections.sort(sorted);
                    StringBuilder sb = new StringBuilder();
                    for (String s : sorted) {
                        sb.append(s);
                    }
                    value = sb.toString();
                }
                break;
            }
            case "judge":
                value = judgeAnswer == 0 ? null : (judgeAnswer == 1 ? "T" : "F");
                break;
            case "blank": {
                List<String> parts = new ArrayList<>();
                boolean any = false;
                for (int i = 0; i < b.blanksContainer.getChildCount(); i++) {
                    EditText et = (EditText) b.blanksContainer.getChildAt(i);
                    String text = et.getText() == null ? "" : et.getText().toString().trim();
                    if (!text.isEmpty()) {
                        any = true;
                    }
                    parts.add(text);
                }
                value = any ? android.text.TextUtils.join("|", parts) : null;
                break;
            }
            default: {
                String text = b.etEssay.getText() == null ? "" : b.etEssay.getText().toString().trim();
                value = text.isEmpty() ? null : text;
                break;
            }
        }
        if (value == null) {
            answers.remove(q.id);
        } else {
            answers.put(q.id, value);
        }
    }

    private void move(int delta) {
        captureCurrent();
        persist();
        int next = index + delta;
        if (next >= questions.size()) {
            confirmSubmit();
            return;
        }
        if (next < 0) {
            return;
        }
        index = next;
        render();
    }

    private void persist() {
        if (session == null) {
            return;
        }
        final ExamSession s = session;
        final Map<Long, String> copy = new java.util.HashMap<>(answers);
        App.get().io().execute(() -> service.saveAnswers(s, copy));
    }

    private void confirmExit() {
        captureCurrent();
        persist();
        new MaterialAlertDialogBuilder(this)
                .setTitle("退出考试？")
                .setMessage("答题进度会保存，可从模考页继续。倒计时仍在后台按开始时间计算。")
                .setNegativeButton("继续答题", null)
                .setPositiveButton("退出", (d, w) -> finish())
                .show();
    }

    private void confirmSubmit() {
        captureCurrent();
        persist();
        int unanswered = questions.size() - answers.size();
        if (unanswered > 0) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("确认交卷")
                    .setMessage("还有 " + unanswered + " 题未答，确定交卷？")
                    .setNegativeButton("再检查一下", null)
                    .setPositiveButton("交卷", (d, w) -> submitNow())
                    .show();
        } else {
            submitNow();
        }
    }

    private void autoSubmit() {
        if (submitting) {
            return;
        }
        Toast.makeText(this, "倒计时结束，自动交卷", Toast.LENGTH_SHORT).show();
        captureCurrent();
        submitNow();
    }

    private void submitNow() {
        if (submitting || session == null) {
            return;
        }
        submitting = true;
        handler.removeCallbacks(tick);
        captureCurrent();
        final ExamSession s = session;
        final Map<Long, String> finalAnswers = new java.util.HashMap<>(answers);
        App.get().io().execute(() -> {
            ExamResult result = service.submit(s, finalAnswers);
            App.get().runOnUiThread(() -> {
                Toast.makeText(this, "得分 " + trim(result.userScore), Toast.LENGTH_SHORT).show();
                ExamResultActivity.start(this, s.id);
                finish();
            });
        });
    }

    private void showAnswerSheet() {
        captureCurrent();
        DialogAnswerSheetBinding sheet = DialogAnswerSheetBinding.inflate(LayoutInflater.from(this));
        GridLayout grid = sheet.gridAnswers;
        float density = getResources().getDisplayMetrics().density;
        int size = (int) (44 * density);
        int margin = (int) (5 * density);

        final androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("答题卡")
                .setView(sheet.getRoot())
                .setPositiveButton("继续答题", null)
                .create();

        for (int i = 0; i < questions.size(); i++) {
            TextView cell = new TextView(this);
            cell.setText(String.valueOf(i + 1));
            cell.setGravity(Gravity.CENTER);
            cell.setTextSize(14);
            boolean answered = answers.containsKey(questions.get(i).id);
            cell.setTextColor(ContextCompat.getColor(this,
                    answered ? R.color.brand : R.color.text_secondary));
            cell.setBackgroundResource(answered ? R.drawable.bg_option_selected : R.drawable.bg_option);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = size;
            lp.height = size;
            lp.setMargins(margin, margin, margin, margin);
            cell.setLayoutParams(lp);
            final int target = i;
            cell.setOnClickListener(v -> {
                index = target;
                render();
                dialog.dismiss();
            });
            grid.addView(cell);
        }
        dialog.show();
    }

    private static Set<String> lettersOf(String raw) {
        Set<String> out = new LinkedHashSet<>();
        for (char c : raw.toUpperCase().toCharArray()) {
            if (c >= 'A' && c <= 'H') {
                out.add(String.valueOf(c));
            }
        }
        return out;
    }

    private static String trim(double v) {
        return String.valueOf(Math.round(v * 10) / 10.0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!submitting) {
            captureCurrent();
            persist();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(tick);
    }
}
