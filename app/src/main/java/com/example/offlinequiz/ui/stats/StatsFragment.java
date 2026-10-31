package com.example.offlinequiz.ui.stats;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.offlinequiz.App;
import com.example.offlinequiz.R;
import com.example.offlinequiz.data.entity.DailyPractice;
import com.example.offlinequiz.data.model.DayStat;
import com.example.offlinequiz.data.model.MasteryRow;
import com.example.offlinequiz.data.repo.StatsRepository;
import com.example.offlinequiz.databinding.FragmentStatsBinding;
import com.example.offlinequiz.databinding.ItemMasteryBinding;
import com.example.offlinequiz.util.DateUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Statistics tab: volume chart, accuracy, mastery per knowledge tag. */
public class StatsFragment extends Fragment {

    private FragmentStatsBinding b;
    private StatsRepository repository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentStatsBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        repository = new StatsRepository(App.get().db());
        App.get().io().execute(() -> {
            List<DayStat> days = repository.recentDays(7);
            List<MasteryRow> mastery = repository.mastery();
            int total = repository.totalAnswers();
            int correct = repository.correctAnswers();
            int streak = streak(App.get().db().dailyDao().all());
            App.get().runOnUiThread(() -> bind(days, mastery, total, correct, streak));
        });
    }

    private void bind(List<DayStat> days, List<MasteryRow> mastery, int total, int correct,
                      int streak) {
        b.tvStatTotal.setText(String.valueOf(total));
        int accuracy = total == 0 ? 0 : Math.round(correct * 100f / total);
        b.tvStatAccuracy.setText(accuracy + "%");
        b.tvStatStreak.setText(String.valueOf(streak));
        b.barChart.setData(days);

        int weekTotal = 0;
        int weekCorrect = 0;
        for (DayStat d : days) {
            weekTotal += d.total;
            weekCorrect += d.correct;
        }
        int weekAccuracy = weekTotal == 0 ? 0 : Math.round(weekCorrect * 100f / weekTotal);
        b.tvWeekSummary.setText("近 7 天共答 " + weekTotal + " 题，正确率 " + weekAccuracy + "%");

        b.llMastery.removeAllViews();
        if (mastery == null || mastery.isEmpty()) {
            b.tvMasteryEmpty.setVisibility(View.VISIBLE);
            return;
        }
        b.tvMasteryEmpty.setVisibility(View.GONE);
        List<MasteryRow> weak = new ArrayList<>();
        for (MasteryRow row : mastery) {
            if (row.mastery < 50) {
                weak.add(row);
            }
        }
        for (MasteryRow row : mastery) {
            b.llMastery.addView(buildRow(row));
        }
        b.tvMasteryNote.setText(weak.isEmpty()
                ? "掌握度 = 该知识点答对题数 / 做题记录数；当前没有低于 50% 的薄弱项"
                : "薄弱项 " + weak.size() + " 个（掌握度 < 50%，记录 ≥ 3 条），建议优先复习");
    }

    private View buildRow(MasteryRow row) {
        ItemMasteryBinding item = ItemMasteryBinding.inflate(getLayoutInflater(),
                b.llMastery, false);
        item.tvKnowledge.setText(row.knowledge);
        int percent = (int) Math.round(row.mastery);
        item.tvMastery.setText(percent + "%");
        item.progressMastery.setProgress(Math.max(0, Math.min(100, percent)));
        item.tvSamples.setText("共 " + row.total + " 条记录 · 答对 " + row.correct + " 条");

        int colorRes;
        int bgRes;
        String level;
        if (percent >= 80) {
            colorRes = R.color.success;
            bgRes = R.drawable.bg_pill_success;
            level = "熟练";
        } else if (percent >= 50) {
            colorRes = R.color.warning;
            bgRes = R.drawable.bg_pill_warning;
            level = "一般";
        } else {
            colorRes = R.color.danger;
            bgRes = R.drawable.bg_pill_danger;
            level = "薄弱";
        }
        int color = ContextCompat.getColor(requireContext(), colorRes);
        item.tvLevel.setText(level);
        item.tvLevel.setBackgroundResource(bgRes);
        item.tvLevel.setTextColor(color);
        item.progressMastery.setProgressTintList(ColorStateList.valueOf(color));
        if (percent < 50) {
            item.tvKnowledge.setTextColor(color);
        }
        return item.getRoot();
    }

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
