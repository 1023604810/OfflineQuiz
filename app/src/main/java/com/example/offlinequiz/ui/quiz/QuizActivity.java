package com.example.offlinequiz.ui.quiz;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.offlinequiz.App;
import com.example.offlinequiz.R;
import com.example.offlinequiz.data.entity.DailyPractice;
import com.example.offlinequiz.data.entity.Question;
import com.example.offlinequiz.data.repo.PracticeRepository;
import com.example.offlinequiz.databinding.ActivityQuizBinding;
import com.example.offlinequiz.engine.AnalysisFactory;
import com.example.offlinequiz.engine.AnswerEngine;
import com.example.offlinequiz.engine.DifficultyAnalyzer;
import com.example.offlinequiz.engine.GradeResult;
import com.example.offlinequiz.service.DailyPracticeService;
import com.example.offlinequiz.util.DateUtil;
import com.example.offlinequiz.util.QuestionText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Answering screen used by 全部练习 / 每日一练 / 难度练习 / 收藏 / 错题.
 *
 * <p>Two presentation modes share the same progress: 刷题模式 grades a submitted answer,
 * 背题模式 reveals the correct answer and explanation immediately. Switching modes keeps
 * the current question and selection, and never records a practice result in 背题模式.</p>
 */
public class QuizActivity extends AppCompatActivity {

    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_DIFFICULTY = "difficulty";
    private static final String EXTRA_COUNT = "count";
    private static final String EXTRA_IDS = "ids";

    private static final int MODE_DAILY = 1;
    private static final int MODE_FAVORITE = 2;
    private static final int MODE_WRONG = 3;
    private static final int MODE_DIFFICULTY = 4;
    private static final int MODE_ALL = 5;

    private ActivityQuizBinding b;
    private PracticeRepository repository;
    private DailyPracticeService dailyService;

    private final List<Question> questions = new ArrayList<>();
    private int mode = MODE_DAILY;
    private int difficulty = 2;
    private int index;
    private DailyPractice daily;
    private boolean submitted;
    private boolean reciteMode;
    /** -1 = 未选择, 1 = 正确, 0 = 错误. */
    private int judgeAnswer = -1;
    private long questionStartMs;
    private final Map<Long, String> blankInputs = new HashMap<>();

    /** 答题浏览路径：返回上一题沿着这条栈回退，保证与用户的浏览顺序一致。 */
    private final List<Integer> history = new ArrayList<>();
    /** 每道题已填写的答案与提交状态，用于返回时完整回显。 */
    private final Map<Long, QuestionState> states = new HashMap<>();

    /** Per-question UI state that survives navigating away and back. */
    private static class QuestionState {
        Set<String> selected = new LinkedHashSet<>();
        int judge = -1;
        List<String> blanks = new ArrayList<>();
        String essay = "";
        boolean submitted;
        GradeResult grade;
        String submittedAnswer;
        boolean selfEvaluated;
        boolean selfEvalCorrect;
        int timeSpentSeconds;
    }

    public static void startDaily(Context context) {
        Intent intent = new Intent(context, QuizActivity.class);
        intent.putExtra(EXTRA_MODE, MODE_DAILY);
        context.startActivity(intent);
    }

    public static void startDifficulty(Context context, int difficulty, int count) {
        Intent intent = new Intent(context, QuizActivity.class);
        intent.putExtra(EXTRA_MODE, MODE_DIFFICULTY);
        intent.putExtra(EXTRA_DIFFICULTY, difficulty);
        intent.putExtra(EXTRA_COUNT, count);
        context.startActivity(intent);
    }

    public static void startFavorite(Context context, long[] ids) {
        Intent intent = new Intent(context, QuizActivity.class);
        intent.putExtra(EXTRA_MODE, MODE_FAVORITE);
        intent.putExtra(EXTRA_IDS, ids);
        context.startActivity(intent);
    }

    public static void startWrong(Context context, long[] ids) {
        Intent intent = new Intent(context, QuizActivity.class);
        intent.putExtra(EXTRA_MODE, MODE_WRONG);
        intent.putExtra(EXTRA_IDS, ids);
        context.startActivity(intent);
    }

    /** 开始练习：按题库顺序加载全部题目，可从中断处继续。 */
    public static void startAll(Context context) {
        Intent intent = new Intent(context, QuizActivity.class);
        intent.putExtra(EXTRA_MODE, MODE_ALL);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityQuizBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        repository = new PracticeRepository(App.get().db());
        dailyService = new DailyPracticeService(App.get().db().dailyDao(),
                App.get().db().questionDao());

        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_DAILY);
        difficulty = getIntent().getIntExtra(EXTRA_DIFFICULTY, 2);
        reciteMode = App.get().prefs().reciteMode();

        b.btnBack.setOnClickListener(v -> finish());
        b.btnSubmit.setOnClickListener(v -> onSubmitClicked());
        b.btnPrevQuestion.setOnClickListener(v -> goToPreviousQuestion());
        b.btnFavorite.setOnClickListener(v -> toggleFavorite());
        b.btnMode.setOnClickListener(v -> togglePracticeMode());
        b.btnJudgeTrue.setOnClickListener(v -> selectJudge(1));
        b.btnJudgeFalse.setOnClickListener(v -> selectJudge(0));
        b.btnSelfCorrect.setOnClickListener(v -> submitSelfEval(true));
        b.btnSelfWrong.setOnClickListener(v -> submitSelfEval(false));
        b.optionsContainer.setOnSelectionChanged(letters -> {
            // selection is read when submitting
        });
        updateModeButton();

        loadQuestions();
    }

    private void loadQuestions() {
        App.get().io().execute(() -> {
            List<Question> loaded = new ArrayList<>();
            int startIndex = 0;
            if (mode == MODE_ALL) {
                // de-duplicate defensively: one row per question id, stable order
                Map<Long, Question> unique = new LinkedHashMap<>();
                for (Question q : App.get().db().questionDao().getAllOrdered()) {
                    unique.put(q.id, q);
                }
                loaded = new ArrayList<>(unique.values());
                if (!loaded.isEmpty()) {
                    int savedCount = App.get().prefs().allPracticeQuestionCount();
                    long savedId = App.get().prefs().allPracticeQuestionId();
                    if (savedCount == loaded.size() && savedId > 0) {
                        for (int i = 0; i < loaded.size(); i++) {
                            if (loaded.get(i).id == savedId) {
                                startIndex = i;
                                break;
                            }
                        }
                    }
                }
            } else if (mode == MODE_DAILY) {
                daily = dailyService.getOrCreate(DateUtil.todayKey(), App.get().prefs().dailyCount());
                if (daily == null) {
                    App.get().runOnUiThread(() -> {
                        Toast.makeText(this, "题库为空，请先导入题库", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }
                loaded = ordered(dailyService.questionIds(daily));
                startIndex = Math.min(daily.doneCount, Math.max(0, loaded.size() - 1));
            } else if (mode == MODE_DIFFICULTY) {
                int count = getIntent().getIntExtra(EXTRA_COUNT, 10);
                loaded = App.get().db().questionDao().randomByDifficulty(difficulty, count);
                if (loaded.isEmpty()) {
                    loaded = App.get().db().questionDao().random(count);
                }
            } else {
                long[] ids = getIntent().getLongArrayExtra(EXTRA_IDS);
                List<Long> list = new ArrayList<>();
                if (ids != null) {
                    for (long id : ids) {
                        list.add(id);
                    }
                }
                loaded = ordered(list);
            }
            final List<Question> result = loaded;
            final int start = startIndex;
            App.get().runOnUiThread(() -> {
                if (result.isEmpty()) {
                    Toast.makeText(this, "没有可作答的题目", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                questions.clear();
                questions.addAll(result);
                index = Math.min(start, questions.size() - 1);
                render();
            });
        });
    }

    private List<Question> ordered(List<Long> ids) {
        List<Question> loaded = App.get().db().questionDao().getByIds(ids);
        Map<Long, Question> byId = new HashMap<>();
        for (Question q : loaded) {
            byId.put(q.id, q);
        }
        List<Question> out = new ArrayList<>();
        for (Long id : ids) {
            Question q = byId.get(id);
            if (q != null) {
                out.add(q);
            }
        }
        return out;
    }

    private Question current() {
        return questions.get(index);
    }

    private void render() {
        Question q = current();
        submitted = false;
        judgeAnswer = -1;
        blankInputs.clear();
        questionStartMs = System.currentTimeMillis();

        b.tvType.setText(QuestionText.typeLabel(q.type));
        String difficultyText = QuestionText.difficultyLabel(q.difficulty);
        if (q.difficultyScore > 0) {
            difficultyText += " · " + q.difficultyScore + " 分";
        }
        b.tvDifficulty.setText(difficultyText);
        b.tvDifficulty.setOnClickListener(v -> showDifficultyDetail(q));
        b.tvKnowledge.setText(q.knowledge == null ? "" : q.knowledge);
        b.tvStem.setText(q.stem);

        b.optionsContainer.setVisibility(View.GONE);
        b.judgeContainer.setVisibility(View.GONE);
        b.blanksContainer.setVisibility(View.GONE);
        b.essayWrap.setVisibility(View.GONE);
        b.cardResult.setVisibility(View.GONE);
        b.selfEvalContainer.setVisibility(View.GONE);
        b.tvUserAnswer.setVisibility(View.VISIBLE);
        resetJudgeButtons();

        String type = q.type == null ? "single" : q.type;
        switch (type) {
            case "multiple":
                showOptions(q, true);
                break;
            case "judge":
                b.judgeContainer.setVisibility(View.VISIBLE);
                break;
            case "blank":
                showBlanks(q);
                break;
            case "essay":
                b.essayWrap.setVisibility(View.VISIBLE);
                b.etEssay.setText("");
                break;
            default:
                showOptions(q, false);
                break;
        }

        b.btnSubmit.setText(reciteMode ? "下一题" : "提交答案");
        b.progressQuiz.setMax(questions.size());
        b.progressQuiz.setProgress(index + 1);
        b.tvTitle.setText(modeTitle() + " · " + (index + 1) + "/" + questions.size());
        updateModeButton();
        updatePreviousButton();
        b.scrollQuiz.scrollTo(0, 0);

        if (mode == MODE_ALL) {
            App.get().prefs().saveAllPracticeProgress(q.id, questions.size());
        }
        restoreState(q);
        if (reciteMode && !submitted) {
            showReciteView(q);
        }
        loadFavoriteState(q);
    }

    private String modeTitle() {
        switch (mode) {
            case MODE_FAVORITE:
                return "收藏练习";
            case MODE_WRONG:
                return "错题重做";
            case MODE_DIFFICULTY:
                return "难度练习";
            case MODE_ALL:
                return "全部练习";
            default:
                return "每日一练";
        }
    }

    private void updateModeButton() {
        if (b.btnMode == null) {
            return;
        }
        if (reciteMode) {
            b.btnMode.setText("背题模式");
            b.btnMode.setBackgroundResource(R.drawable.bg_pill_warning);
            b.btnMode.setTextColor(ContextCompat.getColor(this, R.color.warning));
        } else {
            b.btnMode.setText("刷题模式");
            b.btnMode.setBackgroundResource(R.drawable.bg_chip);
            b.btnMode.setTextColor(ContextCompat.getColor(this, R.color.brand));
        }
    }

    /** 背题/刷题双向切换：保留当前题号与已选选项。 */
    private void togglePracticeMode() {
        reciteMode = !reciteMode;
        App.get().prefs().setReciteMode(reciteMode);
        updateModeButton();
        Question q = current();
        if (reciteMode) {
            if (!submitted) {
                showReciteView(q);
                b.btnSubmit.setText(index + 1 >= questions.size() ? "完成" : "下一题");
            }
        } else {
            if (!submitted) {
                b.cardResult.setVisibility(View.GONE);
                b.tvUserAnswer.setVisibility(View.VISIBLE);
                b.optionsContainer.clearReveal();
                b.optionsContainer.setLocked(false);
                b.selfEvalContainer.setVisibility(View.GONE);
                b.btnSubmit.setText("提交答案");
            } else {
                b.tvUserAnswer.setVisibility(View.VISIBLE);
            }
        }
        Toast.makeText(this, reciteMode ? "已切换到背题模式：自动显示答案与解析"
                : "已切换到刷题模式：先作答再判分", Toast.LENGTH_SHORT).show();
    }

    /** 背题模式：直接展示正确答案与完整解析，不记录做题结果。 */
    private void showReciteView(Question q) {
        b.cardResult.setVisibility(View.VISIBLE);
        b.tvResult.setText("背题模式 · 已显示答案与解析");
        b.tvResult.setTextColor(ContextCompat.getColor(this, R.color.warning));
        b.ivResult.setImageResource(R.drawable.ic_nav_practice);
        b.ivResult.setColorFilter(ContextCompat.getColor(this, R.color.warning));

        b.tvCorrectAnswer.setText("标准答案：" + answerText(q));
        b.tvUserAnswer.setVisibility(View.GONE);
        b.selfEvalContainer.setVisibility(View.GONE);
        b.tvAnalysisLabel.setVisibility(View.VISIBLE);
        b.tvAnalysis.setVisibility(View.VISIBLE);
        b.tvAnalysis.setText(ensureAnalysis(q));

        if ("single".equals(q.type) || "multiple".equals(q.type)) {
            b.optionsContainer.revealAnswer(q.answer, null);
            b.optionsContainer.setLocked(true);
        }
    }

    private void showOptions(Question q, boolean multiple) {
        b.optionsContainer.setVisibility(View.VISIBLE);
        b.optionsContainer.setMultiple(multiple);
        b.optionsContainer.setOptions(QuestionText.optionList(q));
        b.optionsContainer.setLocked(false);
        b.optionsContainer.clearSelection();
    }

    private void showBlanks(Question q) {
        b.blanksContainer.setVisibility(View.VISIBLE);
        b.blanksContainer.removeAllViews();
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
            et.setInputType(InputType.TYPE_CLASS_TEXT);
            et.setTextSize(15);
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = i == count - 1 ? 0 : (int) (10 * density);
            et.setLayoutParams(lp);
            final int idx = i;
            et.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count1, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count1) {
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    blankInputs.put((long) idx, s.toString());
                }
            });
            blankInputs.put((long) i, "");
            b.blanksContainer.addView(et);
        }
    }

    private void selectJudge(int value) {
        if (submitted) {
            return;
        }
        judgeAnswer = value;
        applyJudgeSelection(value);
    }

    /** Paints the two judge buttons for the given selection (1 正确 / 0 错误). */
    private void applyJudgeSelection(int value) {
        boolean yes = value == 1;
        b.btnJudgeTrue.setStrokeColorResource(yes ? R.color.brand : R.color.divider);
        b.btnJudgeTrue.setTextColor(ContextCompat.getColor(this,
                yes ? R.color.brand : R.color.text_primary));
        b.btnJudgeFalse.setStrokeColorResource(!yes ? R.color.brand : R.color.divider);
        b.btnJudgeFalse.setTextColor(ContextCompat.getColor(this,
                !yes ? R.color.brand : R.color.text_primary));
    }

    private void resetJudgeButtons() {
        b.btnJudgeTrue.setStrokeColorResource(R.color.divider);
        b.btnJudgeFalse.setStrokeColorResource(R.color.divider);
        b.btnJudgeTrue.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        b.btnJudgeFalse.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
    }

    private String collectAnswer() {
        Question q = current();
        String type = q.type == null ? "single" : q.type;
        switch (type) {
            case "multiple":
            case "single": {
                Set<String> letters = b.optionsContainer.getSelected();
                if (letters.isEmpty()) {
                    return null;
                }
                List<String> sorted = new ArrayList<>(letters);
                java.util.Collections.sort(sorted);
                StringBuilder sb = new StringBuilder();
                for (String s : sorted) {
                    sb.append(s);
                }
                return sb.toString();
            }
            case "judge":
                return judgeAnswer < 0 ? null : (judgeAnswer == 1 ? "T" : "F");
            case "blank": {
                List<String> parts = new ArrayList<>();
                boolean any = false;
                for (int i = 0; i < QuestionText.blankCount(q); i++) {
                    String v = blankInputs.get((long) i);
                    v = v == null ? "" : v.trim();
                    if (!v.isEmpty()) {
                        any = true;
                    }
                    parts.add(v);
                }
                return any ? android.text.TextUtils.join("|", parts) : null;
            }
            default: {
                String text = b.etEssay.getText() == null ? "" : b.etEssay.getText().toString().trim();
                return text.isEmpty() ? null : text;
            }
        }
    }

    private void onSubmitClicked() {
        if (reciteMode) {
            next();
            return;
        }
        if (submitted) {
            next();
            return;
        }
        final Question q = current();
        final String userAnswer = collectAnswer();
        if (userAnswer == null) {
            Toast.makeText(this, "请先作答", Toast.LENGTH_SHORT).show();
            return;
        }
        submitted = true;
        final GradeResult grade = AnswerEngine.grade(q, userAnswer);
        QuestionState state = stateOf(q);
        state.submitted = true;
        state.grade = grade;
        state.submittedAnswer = userAnswer;
        if (grade.grade == GradeResult.Grade.SELF_EVAL) {
            showResult(grade, userAnswer);
            return;
        }
        record(q, userAnswer, grade.toDbFlag());
        showResult(grade, userAnswer);
        if (mode == MODE_WRONG && grade.grade == GradeResult.Grade.CORRECT) {
            askRemoveWrong(q);
        }
    }

    private void showResult(GradeResult grade, String userAnswer) {
        Question q = current();
        b.cardResult.setVisibility(View.VISIBLE);
        b.tvUserAnswer.setVisibility(View.VISIBLE);

        int color;
        int icon;
        String text;
        switch (grade.grade) {
            case CORRECT:
                color = R.color.success;
                icon = R.drawable.ic_check_circle;
                text = "回答正确";
                break;
            case PARTIAL:
                color = R.color.warning;
                icon = R.drawable.ic_check_circle;
                text = "部分正确（半对）";
                break;
            case SELF_EVAL:
                color = R.color.brand;
                icon = R.drawable.ic_nav_practice;
                text = "已提交，请对照参考答案自评";
                b.selfEvalContainer.setVisibility(View.VISIBLE);
                break;
            default:
                color = R.color.danger;
                icon = R.drawable.ic_close_circle;
                text = "回答错误";
                break;
        }
        b.tvResult.setText(text);
        b.tvResult.setTextColor(ContextCompat.getColor(this, color));
        b.ivResult.setImageResource(icon);
        b.ivResult.setColorFilter(ContextCompat.getColor(this, color));

        b.tvCorrectAnswer.setText("标准答案：" + answerText(q));
        b.tvUserAnswer.setText("你的答案：" + displayUserAnswer(q, userAnswer));
        b.tvAnalysisLabel.setVisibility(View.VISIBLE);
        b.tvAnalysis.setVisibility(View.VISIBLE);
        b.tvAnalysis.setText(ensureAnalysis(q));

        if ("single".equals(q.type) || "multiple".equals(q.type)) {
            b.optionsContainer.revealAnswer(q.answer, userAnswer);
        }
        b.btnSubmit.setText(index + 1 >= questions.size() ? "完成" : "下一题");
    }

    private String answerText(Question q) {
        String correct = QuestionText.answerDisplay(q, q.answer);
        if ("blank".equals(q.type)) {
            correct = q.answer == null ? "" : q.answer.replace("|", " / ");
        }
        return correct;
    }

    private String ensureAnalysis(Question q) {
        if (q.analysis != null && !q.analysis.trim().isEmpty()) {
            return q.analysis;
        }
        String generated = AnalysisFactory.generate(q);
        q.analysis = generated;
        return generated;
    }

    private String displayUserAnswer(Question q, String userAnswer) {
        if ("judge".equals(q.type)) {
            return "T".equals(userAnswer) ? "正确" : "错误";
        }
        return userAnswer;
    }

    private void submitSelfEval(boolean correct) {
        Question q = current();
        submitted = true;
        QuestionState state = stateOf(q);
        state.submitted = true;
        state.selfEvaluated = true;
        state.selfEvalCorrect = correct;
        record(q, correct ? "自评：答对了" : "自评：答错了", correct ? 1 : 0);
        b.selfEvalContainer.setVisibility(View.GONE);
        b.tvResult.setText(correct ? "已记录：答对了" : "已记录：答错了");
        b.tvResult.setTextColor(ContextCompat.getColor(this,
                correct ? R.color.success : R.color.danger));
        b.btnSubmit.setText(index + 1 >= questions.size() ? "完成" : "下一题");
    }

    private void record(Question q, String userAnswer, int flag) {
        QuestionState state = stateOf(q);
        state.timeSpentSeconds += elapsedSeconds();
        questionStartMs = System.currentTimeMillis();
        final int cost = Math.max(1, state.timeSpentSeconds);
        final DailyPractice dailyRef = daily;
        App.get().io().execute(() -> {
            repository.recordAnswer(q, userAnswer, flag, cost);
            if (dailyRef != null) {
                dailyService.markAnswered(dailyRef, flag == 1);
            }
        });
    }

    private void askRemoveWrong(Question q) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("已掌握这道题？")
                .setMessage("回答正确。要把这道题移出错题本吗？")
                .setNegativeButton("保留", null)
                .setPositiveButton("移出", (d, w) -> App.get().io().execute(() -> {
                    repository.removeWrong(q.id);
                    App.get().runOnUiThread(() ->
                            Toast.makeText(this, "已移出错题本", Toast.LENGTH_SHORT).show());
                }))
                .show();
    }

    private void showDifficultyDetail(Question q) {
        String reason = q.difficultyReason;
        if (reason == null || reason.trim().isEmpty()) {
            reason = "该题尚未完成难度分级，导入或启动后会自动计算。";
        }
        String message = "等级：" + QuestionText.difficultyLabel(q.difficulty)
                + (q.difficultyScore > 0 ? "（" + q.difficultyScore + " 分）" : "")
                + "\n\n" + reason;
        new MaterialAlertDialogBuilder(this)
                .setTitle("难度分级依据")
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show();
    }

    private QuestionState stateOf(Question q) {
        QuestionState state = states.get(q.id);
        if (state == null) {
            state = new QuestionState();
            states.put(q.id, state);
        }
        return state;
    }

    /** Reads the current screen back into the per-question state. */
    private void captureCurrent() {
        if (questions.isEmpty()) {
            return;
        }
        Question q = current();
        QuestionState state = stateOf(q);
        String type = q.type == null ? "single" : q.type;
        switch (type) {
            case "multiple":
            case "single":
                state.selected = new LinkedHashSet<>(b.optionsContainer.getSelected());
                break;
            case "judge":
                state.judge = judgeAnswer;
                break;
            case "blank": {
                List<String> blanks = new ArrayList<>();
                for (int i = 0; i < b.blanksContainer.getChildCount(); i++) {
                    EditText et = (EditText) b.blanksContainer.getChildAt(i);
                    blanks.add(et.getText() == null ? "" : et.getText().toString());
                }
                state.blanks = blanks;
                break;
            }
            default:
                state.essay = b.etEssay.getText() == null ? "" : b.etEssay.getText().toString();
                break;
        }
    }

    /** Restores a previously visited question: answers, submitted result and 背题 overlay. */
    private void restoreState(Question q) {
        QuestionState state = states.get(q.id);
        if (state == null) {
            return;
        }
        String type = q.type == null ? "single" : q.type;
        if (("single".equals(type) || "multiple".equals(type)) && !state.selected.isEmpty()) {
            b.optionsContainer.setSelected(state.selected);
        }
        if ("judge".equals(type) && state.judge >= 0) {
            judgeAnswer = state.judge;
            applyJudgeSelection(state.judge);
        }
        if ("blank".equals(type)) {
            for (int i = 0; i < b.blanksContainer.getChildCount() && i < state.blanks.size(); i++) {
                EditText et = (EditText) b.blanksContainer.getChildAt(i);
                et.setText(state.blanks.get(i));
                et.setSelection(et.getText() == null ? 0 : et.getText().length());
            }
        }
        if ("essay".equals(type) && state.essay != null) {
            b.etEssay.setText(state.essay);
        }
        if (state.submitted && state.grade != null) {
            submitted = true;
            showResult(state.grade, state.submittedAnswer);
            if (state.selfEvaluated) {
                b.selfEvalContainer.setVisibility(View.GONE);
                b.tvResult.setText(state.selfEvalCorrect ? "已记录：答对了" : "已记录：答错了");
                b.tvResult.setTextColor(ContextCompat.getColor(this,
                        state.selfEvalCorrect ? R.color.success : R.color.danger));
                b.btnSubmit.setText(index + 1 >= questions.size() ? "完成" : "下一题");
            }
        }
    }

    private int elapsedSeconds() {
        return (int) Math.max(0, (System.currentTimeMillis() - questionStartMs) / 1000);
    }

    private void accumulateTime() {
        if (questions.isEmpty()) {
            return;
        }
        stateOf(current()).timeSpentSeconds += elapsedSeconds();
        questionStartMs = System.currentTimeMillis();
    }

    private boolean canGoBack() {
        return index > 0 || !history.isEmpty();
    }

    private void updatePreviousButton() {
        boolean enabled = canGoBack();
        b.btnPrevQuestion.setEnabled(enabled);
        b.btnPrevQuestion.setAlpha(enabled ? 1f : 0.4f);
    }

    /** 沿浏览历史栈回到上一题（保留已填答案、判分结果与收藏状态）。 */
    private void goToPreviousQuestion() {
        if (!canGoBack()) {
            return;
        }
        captureCurrent();
        accumulateTime();
        if (!history.isEmpty()) {
            index = history.remove(history.size() - 1);
        } else if (index > 0) {
            index--;
        }
        if (index < 0) {
            index = 0;
        }
        render();
    }

    private void next() {
        if (index + 1 >= questions.size()) {
            finishSession();
            return;
        }
        captureCurrent();
        accumulateTime();
        history.add(index);
        index++;
        render();
    }

    private void finishSession() {
        if (mode == MODE_ALL) {
            App.get().prefs().clearAllPracticeProgress();
            new MaterialAlertDialogBuilder(this)
                    .setTitle("全部题目练习完成")
                    .setMessage("共 " + questions.size() + " 题，已按顺序练习完毕。")
                    .setPositiveButton("再练一遍", (d, w) -> {
                        history.clear();
                        states.clear();
                        index = 0;
                        render();
                    })
                    .setNegativeButton("完成", (d, w) -> finish())
                    .show();
            return;
        }
        if (mode == MODE_DAILY && daily != null) {
            int done = daily.totalCount;
            int accuracy = done == 0 ? 0 : Math.round(daily.correctCount * 100f / done);
            new MaterialAlertDialogBuilder(this)
                    .setTitle("今日练习完成")
                    .setMessage("共 " + done + " 题，答对 " + daily.correctCount
                            + " 题，正确率 " + accuracy + "%。")
                    .setPositiveButton("完成", (d, w) -> finish())
                    .setOnDismissListener(d -> finish())
                    .show();
        } else {
            Toast.makeText(this, "本轮练习完成", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadFavoriteState(Question q) {
        App.get().io().execute(() -> {
            final boolean fav = repository.isFavorite(q.id);
            App.get().runOnUiThread(() -> {
                if (current() == q) {
                    b.btnFavorite.setImageResource(fav
                            ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
                }
            });
        });
    }

    private void toggleFavorite() {
        final Question q = current();
        App.get().io().execute(() -> {
            final boolean nowFavorite = repository.toggleFavorite(q.id);
            App.get().runOnUiThread(() -> {
                b.btnFavorite.setImageResource(nowFavorite
                        ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
                Toast.makeText(this, nowFavorite ? "已收藏" : "已取消收藏",
                        Toast.LENGTH_SHORT).show();
            });
        });
    }
}
