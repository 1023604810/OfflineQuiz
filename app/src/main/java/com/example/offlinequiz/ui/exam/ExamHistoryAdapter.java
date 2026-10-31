package com.example.offlinequiz.ui.exam;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.offlinequiz.data.entity.ExamSession;
import com.example.offlinequiz.databinding.ItemExamHistoryBinding;
import com.example.offlinequiz.service.ExamService;
import com.example.offlinequiz.util.DateUtil;

import java.util.ArrayList;
import java.util.List;

public class ExamHistoryAdapter extends RecyclerView.Adapter<ExamHistoryAdapter.VH> {

    public interface OnClick {
        void onClick(ExamSession session);
    }

    private final List<ExamSession> items = new ArrayList<>();
    private final OnClick onClick;

    public ExamHistoryAdapter(OnClick onClick) {
        this.onClick = onClick;
    }

    public void submit(List<ExamSession> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemExamHistoryBinding b = ItemExamHistoryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ExamSession s = items.get(position);
        holder.b.tvTitle.setText(s.title == null ? "模拟考试" : s.title);
        holder.b.tvMeta.setText(DateUtil.formatTime(s.submitTime) + " · "
                + ExamService.questionCount(s) + " 题 · " + s.timeLimit + " 分钟");
        holder.b.tvScore.setText(String.valueOf(Math.round(s.userScore)));
        holder.b.getRoot().setOnClickListener(v -> onClick.onClick(s));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemExamHistoryBinding b;

        VH(ItemExamHistoryBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
