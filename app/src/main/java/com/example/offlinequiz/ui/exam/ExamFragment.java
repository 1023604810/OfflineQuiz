package com.example.offlinequiz.ui.exam;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.offlinequiz.App;
import com.example.offlinequiz.data.entity.ExamSession;
import com.example.offlinequiz.databinding.FragmentExamBinding;
import com.example.offlinequiz.service.ExamService;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Exam tab: start a paper, resume an unfinished one, browse recent results. */
public class ExamFragment extends Fragment {

    private FragmentExamBinding b;
    private ExamHistoryAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentExamBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        adapter = new ExamHistoryAdapter(session ->
                ExamResultActivity.start(requireContext(), session.id));
        b.rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rvHistory.setAdapter(adapter);

        App.get().db().examDao().historyLive(5).observe(getViewLifecycleOwner(), list -> {
            adapter.submit(list);
            b.tvHistoryEmpty.setVisibility(list == null || list.isEmpty()
                    ? View.VISIBLE : View.GONE);
        });

        b.btnStartExam.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ExamSetupActivity.class)));

        b.btnResumeExam.setOnClickListener(v -> {
            if (b.cardUnfinished.getTag() instanceof Long) {
                ExamActivity.start(requireContext(), (Long) b.cardUnfinished.getTag());
            }
        });

        b.btnDiscardExam.setOnClickListener(v -> {
            Object tag = b.cardUnfinished.getTag();
            if (!(tag instanceof Long)) {
                return;
            }
            long id = (Long) tag;
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("放弃这套试卷？")
                    .setMessage("试卷与已答内容将被删除，不可恢复。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("放弃", (d, w) -> App.get().io().execute(() -> {
                        App.get().db().examDao().deleteById(id);
                        App.get().runOnUiThread(() -> {
                            b.cardUnfinished.setVisibility(View.GONE);
                            b.cardUnfinished.setTag(null);
                        });
                    }))
                    .show();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        App.get().io().execute(() -> {
            final int total = App.get().db().questionDao().count();
            final ExamSession unfinished = App.get().db().examDao().unfinished();
            App.get().runOnUiThread(() -> {
                b.tvExamPool.setText("题库共 " + total + " 题，可自由配置题型与难度");
                b.btnStartExam.setEnabled(total > 0);
                if (unfinished != null) {
                    b.cardUnfinished.setTag(unfinished.id);
                    b.cardUnfinished.setVisibility(View.VISIBLE);
                    b.tvUnfinishedMeta.setText(unfinished.title + " · "
                            + ExamService.questionCount(unfinished) + " 题 · 限时 "
                            + unfinished.timeLimit + " 分钟 · 开始于 "
                            + com.example.offlinequiz.util.DateUtil.formatTime(unfinished.startTime));
                } else {
                    b.cardUnfinished.setVisibility(View.GONE);
                    b.cardUnfinished.setTag(null);
                }
            });
        });
    }
}
