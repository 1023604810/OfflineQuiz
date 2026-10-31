package com.example.offlinequiz.ui.practice;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.offlinequiz.App;
import com.example.offlinequiz.R;
import com.example.offlinequiz.data.entity.DailyPractice;
import com.example.offlinequiz.databinding.FragmentPracticeBinding;
import com.example.offlinequiz.service.DailyPracticeService;
import com.example.offlinequiz.ui.MainActivity;
import com.example.offlinequiz.ui.imports.ImportActivity;
import com.example.offlinequiz.ui.quiz.QuizActivity;
import com.example.offlinequiz.ui.settings.SettingsActivity;
import com.example.offlinequiz.util.DateUtil;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Home tab: today's daily practice, bank overview, difficulty practice, bank management. */
public class PracticeFragment extends Fragment {

    private FragmentPracticeBinding b;
    private DailyPracticeService dailyService;
    private DailyPractice daily;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentPracticeBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        dailyService = new DailyPracticeService(App.get().db().dailyDao(), App.get().db().questionDao());

        b.tvToday.setText(DateUtil.todayKey() + " · " + DateUtil.weekdayLabel(System.currentTimeMillis()));

        App.get().db().questionDao().countLive().observe(getViewLifecycleOwner(), count -> {
            boolean empty = count == null || count == 0;
            b.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            b.content.setVisibility(empty ? View.GONE : View.VISIBLE);
            if (!empty) {
                b.tvBankTotal.setText(String.valueOf(count));
                b.tvAllChip.setText("全部 " + count + " 题");
                b.btnStartAll.setEnabled(true);
            } else {
                b.btnStartAll.setEnabled(false);
            }
        });
        App.get().db().wrongDao().countLive().observe(getViewLifecycleOwner(),
                count -> b.tvWrongTotal.setText(String.valueOf(count == null ? 0 : count)));
        App.get().db().favoriteDao().countLive().observe(getViewLifecycleOwner(),
                count -> b.tvFavoriteTotal.setText(String.valueOf(count == null ? 0 : count)));

        b.btnSettings.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SettingsActivity.class)));
        b.rowImport.setOnClickListener(v -> openImport(false));
        b.btnImportLocal.setOnClickListener(v -> openImport(false));
        b.rowBuiltinImport.setOnClickListener(v -> openImport(true));
        b.btnImportBuiltin.setOnClickListener(v -> openImport(true));

        // repository ships without question data: hide the bundled-bank entries until
        // a bank is placed under assets/questionbank/ locally
        boolean builtinAvailable = com.example.offlinequiz.util.BuiltinBank.available(requireContext());
        b.btnImportBuiltin.setVisibility(builtinAvailable ? View.VISIBLE : View.GONE);
        b.rowBuiltinImport.setVisibility(builtinAvailable ? View.VISIBLE : View.GONE);

        b.btnDaily.setOnClickListener(v -> {
            if (daily == null) {
                return;
            }
            if (daily.doneCount >= daily.totalCount) {
                Intent intent = new Intent(requireContext(), MainActivity.class);
                intent.putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_STATS);
                startActivity(intent);
            } else {
                QuizActivity.startDaily(requireContext());
            }
        });

        b.btnDiffEasy.setOnClickListener(v -> startDifficulty(1));
        b.btnDiffMedium.setOnClickListener(v -> startDifficulty(2));
        b.btnDiffHard.setOnClickListener(v -> startDifficulty(3));

        b.btnStartAll.setOnClickListener(v -> QuizActivity.startAll(requireContext()));
        b.btnHomeMode.setOnClickListener(v -> {
            boolean recite = !App.get().prefs().reciteMode();
            App.get().prefs().setReciteMode(recite);
            updateHomeModeButton();
            Toast.makeText(requireContext(),
                    recite ? "已切换到背题模式：进入练习后直接看答案与解析"
                            : "已切换到刷题模式：先作答再判分",
                    Toast.LENGTH_SHORT).show();
        });
        updateHomeModeButton();
    }

    private void updateHomeModeButton() {
        boolean recite = App.get().prefs().reciteMode();
        b.btnHomeMode.setText(recite ? "背题模式" : "刷题模式");
        b.btnHomeMode.setBackgroundResource(recite
                ? R.drawable.bg_pill_warning : R.drawable.bg_chip);
        b.btnHomeMode.setTextColor(androidx.core.content.ContextCompat.getColor(
                requireContext(), recite ? R.color.warning : R.color.brand));
    }

    private void startDifficulty(int difficulty) {
        App.get().io().execute(() -> {
            final int total = App.get().db().questionDao().count();
            App.get().runOnUiThread(() -> {
                if (total == 0) {
                    return;
                }
                QuizActivity.startDifficulty(requireContext(), difficulty, 10);
            });
        });
    }

    private void openImport(boolean builtin) {
        Intent intent = new Intent(requireContext(), ImportActivity.class);
        intent.putExtra(ImportActivity.EXTRA_BUILTIN, builtin);
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateHomeModeButton();
        loadDaily();
    }

    private void loadDaily() {
        final int target = App.get().prefs().dailyCount();
        final String today = DateUtil.todayKey();
        App.get().io().execute(() -> {
            final DailyPractice created = dailyService.getOrCreate(today, target);
            final List<DailyPractice> history = App.get().db().dailyDao().all();
            final int streak = streak(history);
            App.get().runOnUiThread(() -> {
                daily = created;
                if (created == null) {
                    return;
                }
                b.tvDailyChip.setText(created.totalCount + " 题");
                int done = Math.min(created.doneCount, created.totalCount);
                b.tvDailyProgress.setText("今日已完成 " + done + " / " + created.totalCount);
                int percent = created.totalCount == 0 ? 0
                        : Math.round(done * 100f / created.totalCount);
                b.progressDaily.setProgress(percent);
                if (done > 0) {
                    int accuracy = Math.round(created.correctCount * 100f / done);
                    b.tvDailyAccuracy.setText("正确率 " + accuracy + "%");
                } else {
                    b.tvDailyAccuracy.setText("正确率 --");
                }
                if (done >= created.totalCount) {
                    b.btnDaily.setText("今日已完成 · 查看统计");
                } else if (done == 0) {
                    b.btnDaily.setText("开始今日练习");
                } else {
                    b.btnDaily.setText("继续练习（还剩 " + (created.totalCount - done) + " 题）");
                }
                boolean shortage = created.totalCount < target;
                b.tvDailyNote.setVisibility(shortage ? View.VISIBLE : View.GONE);
                if (shortage) {
                    b.tvDailyNote.setText("题库不足 " + target + " 题，已出全部可用题目");
                }
                b.tvToday.setText(DateUtil.todayKey() + " · 连续打卡 " + streak + " 天");
                b.tvDiffNote.setText("从题库随机抽取 10 题，交卷即看解析");
            });
        });
    }

    /** Counts consecutive days (ending today or yesterday) with at least one answered question. */
    private static int streak(List<DailyPractice> history) {
        Set<String> answered = new HashSet<>();
        for (DailyPractice d : history) {
            if (d.doneCount > 0) {
                answered.add(d.dateKey);
            }
        }
        int count = 0;
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < 365; i++) {
            String key = DateUtil.keyOf(cal.getTimeInMillis());
            if (answered.contains(key)) {
                count++;
            } else if (i > 0 || !answered.contains(DateUtil.keyOf(cal.getTimeInMillis() - 86400000L))) {
                break;
            }
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        return count;
    }
}
