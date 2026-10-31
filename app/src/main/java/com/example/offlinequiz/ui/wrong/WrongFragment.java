package com.example.offlinequiz.ui.wrong;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.offlinequiz.App;
import com.example.offlinequiz.data.model.WrongItem;
import com.example.offlinequiz.databinding.FragmentWrongBinding;
import com.example.offlinequiz.ui.quiz.QuizActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/** Wrong book: auto-collected mistakes, swipe to remove, redo all. */
public class WrongFragment extends Fragment {

    private FragmentWrongBinding b;
    private WrongAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentWrongBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        adapter = new WrongAdapter(item ->
                QuizActivity.startWrong(requireContext(), new long[]{item.questionId}));
        b.rvWrong.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rvWrong.setAdapter(adapter);

        App.get().db().wrongDao().itemsLive().observe(getViewLifecycleOwner(), items -> {
            adapter.submit(items);
            int count = items == null ? 0 : items.size();
            b.tvWrongCount.setText("共 " + count + " 题待攻克");
            b.emptyWrong.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
            b.rvWrong.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
            b.btnRedoAll.setEnabled(count > 0);
        });

        b.btnRedoAll.setOnClickListener(v -> App.get().io().execute(() -> {
            List<Long> ids = App.get().db().wrongDao().orderedQuestionIds();
            long[] array = new long[ids.size()];
            for (int i = 0; i < ids.size(); i++) {
                array[i] = ids.get(i);
            }
            App.get().runOnUiThread(() -> {
                if (array.length == 0) {
                    Toast.makeText(requireContext(), "错题本是空的", Toast.LENGTH_SHORT).show();
                } else {
                    QuizActivity.startWrong(requireContext(), array);
                }
            });
        }));

        ItemTouchHelper helper = new ItemTouchHelper(
                new ItemTouchHelper.SimpleCallback(0,
                        ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
                    @Override
                    public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                          @NonNull RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                        int position = vh.getAdapterPosition();
                        WrongItem item = adapter.itemAt(position);
                        if (item == null) {
                            return;
                        }
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle("移出错题本")
                                .setMessage("确定移除这道题吗？")
                                .setNegativeButton("取消", (d, w) -> adapter.notifyItemChanged(position))
                                .setPositiveButton("移除", (d, w) -> App.get().io().execute(() -> {
                                    App.get().db().wrongDao().deleteByQuestion(item.questionId);
                                    App.get().runOnUiThread(() ->
                                            Toast.makeText(requireContext(), "已移除",
                                                    Toast.LENGTH_SHORT).show());
                                }))
                                .setOnCancelListener(d -> adapter.notifyItemChanged(position))
                                .show();
                    }
                });
        helper.attachToRecyclerView(b.rvWrong);
    }
}
