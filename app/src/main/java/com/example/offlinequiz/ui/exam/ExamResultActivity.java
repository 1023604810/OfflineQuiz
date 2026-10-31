package com.example.offlinequiz.ui.exam;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.offlinequiz.App;
import com.example.offlinequiz.R;
import com.example.offlinequiz.data.entity.ExamSession;
import com.example.offlinequiz.databinding.ActivityExamResultBinding;
import com.example.offlinequiz.engine.ExamResult;
import com.example.offlinequiz.service.ExamConfig;
import com.example.offlinequiz.service.ExamService;
import com.example.offlinequiz.util.QuestionText;

import java.util.Map;

/** Score page: total, accuracy, per-type distribution and weak knowledge tags. */
public class ExamResultActivity extends AppCompatActivity {

    private static final String EXTRA_SESSION_ID = "session_id";

    private ActivityExamResultBinding b;

    public static void start(Context context, long sessionId) {
        Intent intent = new Intent(context, ExamResultActivity.class);
        intent.putExtra(EXTRA_SESSION_ID, sessionId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityExamResultBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.btnBackHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, com.example.offlinequiz.ui.MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
        b.btnAgain.setOnClickListener(v ->
                startActivity(new Intent(this, ExamSetupActivity.class)));

        long id = getIntent().getLongExtra(EXTRA_SESSION_ID, -1);
        App.get().io().execute(() -> {
            ExamSession session = App.get().db().examDao().getById(id);
            if (session == null) {
                App.get().runOnUiThread(this::finish);
                return;
            }
            ExamService service = new ExamService(App.get().db().questionDao(),
                    App.get().db().examDao());
            Map<Long, String> answers = ExamService.parseAnswers(session);
            ExamResult result = service.evaluate(session, answers);
            App.get().runOnUiThread(() -> bind(session, result));
        });
    }

    private void bind(ExamSession session, ExamResult result) {
        b.tvScore.setText(String.valueOf(Math.round(result.userScore * 10) / 10.0));
        b.tvScoreTotal.setText("满分 " + Math.round(result.totalScore) + " 分 · 共 "
                + result.totalQuestions + " 题");
        b.tvAccuracy.setText("正确率 " + result.accuracy() + "%");
        b.tvCorrect.setText(String.valueOf(result.correctCount));
        b.tvPartial.setText(String.valueOf(result.partialCount));
        b.tvWrong.setText(String.valueOf(result.wrongCount));
        b.tvUnanswered.setText(String.valueOf(result.unanswered));

        if (result.shortageNote != null && !result.shortageNote.isEmpty()) {
            b.tvShortage.setVisibility(View.VISIBLE);
            b.tvShortage.setText(result.shortageNote + "，按实际数量组卷");
        }

        b.llByType.removeAllViews();
        for (Map.Entry<String, double[]> entry : result.byType.entrySet()) {
            b.llByType.addView(buildTypeRow(entry.getKey(), entry.getValue()));
        }

        if (!result.weakKnowledge.isEmpty()) {
            b.cardWeak.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            for (String k : result.weakKnowledge) {
                if (sb.length() > 0) {
                    sb.append(" · ");
                }
                sb.append(k);
            }
            b.tvWeak.setText(sb.toString());
        }
    }

    private View buildTypeRow(String type, double[] stat) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) (10 * density), 0, (int) (10 * density));

        TextView name = new TextView(this);
        name.setText(QuestionText.typeLabel(type));
        name.setTextSize(14);
        name.setTextColor(getColor(R.color.text_primary));
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        row.addView(name, nameLp);

        TextView detail = new TextView(this);
        detail.setText("答对 " + (int) stat[1] + " / " + (int) stat[0] + " 题");
        detail.setTextSize(12);
        detail.setTextColor(getColor(R.color.text_secondary));
        row.addView(detail);

        TextView score = new TextView(this);
        score.setText(" " + (Math.round(stat[2] * 10) / 10.0) + " 分");
        score.setTextSize(14);
        score.setTextColor(getColor(R.color.brand));
        score.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(score);

        return row;
    }
}
