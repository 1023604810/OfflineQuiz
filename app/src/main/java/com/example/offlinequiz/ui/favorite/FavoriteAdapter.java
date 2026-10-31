package com.example.offlinequiz.ui.favorite;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.offlinequiz.data.model.FavoriteItem;
import com.example.offlinequiz.databinding.ItemFavoriteBinding;
import com.example.offlinequiz.util.QuestionText;

import java.util.ArrayList;
import java.util.List;

public class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.VH> {

    public interface Listener {
        void onOpen(FavoriteItem item);

        void onUnfavorite(FavoriteItem item);
    }

    private final List<FavoriteItem> items = new ArrayList<>();
    private final Listener listener;

    public FavoriteAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<FavoriteItem> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    public List<Long> ids() {
        List<Long> ids = new ArrayList<>();
        for (FavoriteItem item : items) {
            ids.add(item.questionId);
        }
        return ids;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFavoriteBinding b = ItemFavoriteBinding.inflate(LayoutInflater.from(parent.getContext()),
                parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        FavoriteItem item = items.get(position);
        holder.b.tvStem.setText(item.stem);
        holder.b.tvType.setText(QuestionText.typeLabel(item.type));
        holder.b.tvKnowledge.setText(item.knowledge == null || item.knowledge.isEmpty()
                ? "未分类" : item.knowledge);
        holder.b.getRoot().setOnClickListener(v -> listener.onOpen(item));
        holder.b.btnUnfavorite.setOnClickListener(v -> listener.onUnfavorite(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemFavoriteBinding b;

        VH(ItemFavoriteBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
