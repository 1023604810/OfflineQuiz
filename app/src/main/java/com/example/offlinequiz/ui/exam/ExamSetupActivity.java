package com.example.offlinequiz.ui.exam;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.offlinequiz.App;
import com.example.offlinequiz.data.entity.ExamSession;
import com.example.offlinequiz.databinding.ActivityExamSetupBinding;
import com.example.offlinequiz.service.ExamConfig;
import com.example.offlinequiz.service.ExamService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Exam configuration: counts per type, duration, difficulty range, knowledge scope. */
public class ExamSetupActivity extends AppCompatActivity {

    private ActivityExamSetupBinding b;
    private final List<String> knowledgeOptions = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityExamSetupBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.btnBack.setOnClickListener(v -> finish());
        b.btnBuildPaper.setOnClickListener(v -> buildPaper());

        App.get().io().execute(() -> {
            int single = App.get().db().questionDao().countByType("single");
            int multiple = App.get().db().questionDao().countByType("multiple");
            int judge = App.get().db().questionDao().countByType("judge");
            int blank = App.get().db().questionDao().countByType("blank");
            int essay = App.get().db().questionDao().countByType("essay");
            List<String> tags = App.get().db().questionDao().distinctKnowledge();
            App.get().runOnUiThread(() -> {
                b.tvAvailable.setText("题库存量：单选 " + single + " · 多选 " + multiple
                        + " · 判断 " + judge + " · 填空 " + blank + " · 主观 " + essay);
                b.etSingle.setText(String.valueOf(Math.min(10, single)));
                b.etMultiple.setText(String.valueOf(Math.min(5, multiple)));
                b.etJudge.setText(String.valueOf(Math.min(5, judge)));
                b.etBlank.setText("0");
                b.etEssay.setText("0");
                knowledgeOptions.clear();
                knowledgeOptions.add("全部知识点");
                if (tags != null) {
                    knowledgeOptions.addAll(tags);
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_item, knowledgeOptions);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                b.spinnerKnowledge.setAdapter(adapter);
            });
        });
    }

    private void buildPaper() {
        int single = parseInt(b.etSingle.getText().toString());
        int multiple = parseInt(b.etMultiple.getText().toString());
        int judge = parseInt(b.etJudge.getText().toString());
        int blank = parseInt(b.etBlank.getText().toString());
        int essay = parseInt(b.etEssay.getText().toString());

        ExamConfig config = new ExamConfig();
        config.counts.put("single", single);
        config.counts.put("multiple", multiple);
        config.counts.put("judge", judge);
        config.counts.put("blank", blank);
        config.counts.put("essay", essay);
        config.durationMinutes = b.rb15.isChecked() ? 15 : b.rb60.isChecked() ? 60 : 30;

        List<Integer> difficulties = new ArrayList<>();
        if (b.cbEasy.isChecked()) {
            difficulties.add(1);
        }
        if (b.cbMedium.isChecked()) {
            difficulties.add(2);
        }
        if (b.cbHard.isChecked()) {
            difficulties.add(3);
        }
        config.difficulties = difficulties;

        int selected = b.spinnerKnowledge.getSelectedItemPosition();
        if (selected > 0 && selected < knowledgeOptions.size()) {
            config.knowledge = knowledgeOptions.get(selected);
        }

        if (config.totalQuestions() == 0) {
            showNotice("请至少为一种题型填写大于 0 的数量");
            return;
        }
        if (difficulties.isEmpty()) {
            showNotice("请至少选择一个难度");
            return;
        }

        config.title = "模拟考试 · " + config.totalQuestions() + " 题";
        b.btnBuildPaper.setEnabled(false);
        b.btnBuildPaper.setText("正在组卷…");

        App.get().io().execute(() -> {
            ExamService service = new ExamService(App.get().db().questionDao(),
                    App.get().db().examDao());
            final ExamSession session = service.buildPaper(config);
            App.get().runOnUiThread(() -> {
                b.btnBuildPaper.setEnabled(true);
                b.btnBuildPaper.setText("开始组卷");
                if (session == null || ExamService.questionCount(session) == 0) {
                    showNotice("题库中没有符合条件的题目，试着放宽难度或知识点范围");
                    return;
                }
                if (session.shortageNote != null) {
                    Toast.makeText(this, session.shortageNote, Toast.LENGTH_LONG).show();
                }
                ExamActivity.start(this, session.id);
                finish();
            });
        });
    }

    private void showNotice(String text) {
        b.tvNotice.setVisibility(View.VISIBLE);
        b.tvNotice.setText(text);
    }

    private static int parseInt(String raw) {
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
