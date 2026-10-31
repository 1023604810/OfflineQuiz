package com.example.offlinequiz.ui.wrong;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.offlinequiz.data.model.WrongItem;
import com.example.offlinequiz.databinding.ItemWrongBinding;
import com.example.offlinequiz.util.QuestionText;

import java.util.ArrayList;
import java.util.List;

public class WrongAdapter extends RecyclerView.Adapter<WrongAdapter.VH> {

    public interface OnClick {
        void onClick(WrongItem item);
    }

    private final List<WrongItem> items = new ArrayList<>();
    private final OnClick onClick;

    public WrongAdapter(OnClick onClick) {
        this.onClick = onClick;
    }

    public void submit(List<WrongItem> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    public WrongItem itemAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position) : null;
    }

    public void removeAt(int position) {
        if (position >= 0 && position < items.size()) {
            items.remove(position);
            notifyItemRemoved(position);
        }
    }

    public int sizeNow() {
        return items.size();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemWrongBinding b = ItemWrongBinding.inflate(LayoutInflater.from(parent.getContext()),
                parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        WrongItem item = items.get(position);
        holder.b.tvStem.setText(item.stem);
        holder.b.tvType.setText(QuestionText.typeLabel(item.type));
        holder.b.tvKnowledge.setText(item.knowledge == null || item.knowledge.isEmpty()
                ? "未分类" : item.knowledge);
        holder.b.tvBadge.setText("错 " + item.wrongCount + " 次");
        holder.b.getRoot().setOnClickListener(v -> onClick.onClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemWrongBinding b;

        VH(ItemWrongBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
