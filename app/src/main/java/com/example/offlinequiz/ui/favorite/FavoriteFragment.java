package com.example.offlinequiz.ui.favorite;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.offlinequiz.App;
import com.example.offlinequiz.data.model.FavoriteItem;
import com.example.offlinequiz.databinding.FragmentFavoriteBinding;
import com.example.offlinequiz.ui.quiz.QuizActivity;

import java.util.List;

/** Favorites list plus the "practice only favorites" entry. */
public class FavoriteFragment extends Fragment {

    private FragmentFavoriteBinding b;
    private FavoriteAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentFavoriteBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        adapter = new FavoriteAdapter(new FavoriteAdapter.Listener() {
            @Override
            public void onOpen(FavoriteItem item) {
                QuizActivity.startFavorite(requireContext(), new long[]{item.questionId});
            }

            @Override
            public void onUnfavorite(FavoriteItem item) {
                App.get().io().execute(() -> {
                    App.get().db().favoriteDao().deleteByQuestion(item.questionId);
                    App.get().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "已取消收藏", Toast.LENGTH_SHORT).show());
                });
            }
        });
        b.rvFavorite.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rvFavorite.setAdapter(adapter);

        App.get().db().favoriteDao().itemsLive().observe(getViewLifecycleOwner(), items -> {
            adapter.submit(items);
            int count = items == null ? 0 : items.size();
            b.tvFavCount.setText("共 " + count + " 题");
            b.emptyFavorite.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
            b.rvFavorite.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
            b.btnPracticeFav.setEnabled(count > 0);
        });

        b.btnPracticeFav.setOnClickListener(v -> {
            List<Long> ids = adapter.ids();
            if (ids.isEmpty()) {
                Toast.makeText(requireContext(), "还没有收藏题目", Toast.LENGTH_SHORT).show();
                return;
            }
            long[] array = new long[ids.size()];
            for (int i = 0; i < ids.size(); i++) {
                array[i] = ids.get(i);
            }
            QuizActivity.startFavorite(requireContext(), array);
        });
    }
}
