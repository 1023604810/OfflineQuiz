package com.example.offlinequiz.ui.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.offlinequiz.R;
import com.example.offlinequiz.util.QuestionText;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders the option rows of a choice question and handles selection
 * (single radio behaviour vs multiple checkbox behaviour) plus the answer-review
 * colouring after submitting.
 */
public class OptionGroupView extends LinearLayout {

    public interface OnSelectionChanged {
        void onSelectionChanged(Set<String> letters);
    }

    private boolean multiple;
    private boolean locked;
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<Row> rows = new ArrayList<>();
    @Nullable
    private OnSelectionChanged listener;

    public OptionGroupView(Context context) {
        super(context);
        setOrientation(VERTICAL);
    }

    public OptionGroupView(Context context, @Nullable android.util.AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
    }

    public OptionGroupView(Context context, @Nullable android.util.AttributeSet attrs,
                           int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
    }

    public void setOnSelectionChanged(OnSelectionChanged listener) {
        this.listener = listener;
    }

    public void setMultiple(boolean multiple) {
        this.multiple = multiple;
    }

    public void setOptions(List<String> options) {
        removeAllViews();
        rows.clear();
        selected.clear();
        locked = false;
        LayoutInflater inflater = LayoutInflater.from(getContext());
        int gap = (int) (getResources().getDisplayMetrics().density * 10);

        for (int i = 0; i < options.size(); i++) {
            String[] parts = QuestionText.splitOption(options.get(i));
            String letter = parts[0].isEmpty() ? String.valueOf((char) ('A' + i)) : parts[0];
            String text = parts[1];

            View rowView = inflater.inflate(R.layout.item_option, this, false);
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = i == options.size() - 1 ? 0 : gap;
            rowView.setLayoutParams(lp);

            TextView tvLetter = rowView.findViewById(R.id.tv_letter);
            TextView tvText = rowView.findViewById(R.id.tv_option_text);
            ImageView mark = rowView.findViewById(R.id.iv_option_mark);
            tvLetter.setText(letter);
            tvText.setText(text);

            Row row = new Row(letter, rowView, tvLetter, mark);
            rows.add(row);

            rowView.setOnClickListener(v -> {
                if (locked) {
                    return;
                }
                if (multiple) {
                    if (!selected.remove(letter)) {
                        selected.add(letter);
                    }
                } else {
                    selected.clear();
                    selected.add(letter);
                }
                applySelection();
                if (listener != null) {
                    listener.onSelectionChanged(new LinkedHashSet<>(selected));
                }
            });
            addView(rowView);
        }
    }

    public Set<String> getSelected() {
        return new LinkedHashSet<>(selected);
    }

    public void setSelected(Collection<String> letters) {
        selected.clear();
        if (letters != null) {
            for (String l : letters) {
                if (l != null && !l.trim().isEmpty()) {
                    selected.add(l.trim().toUpperCase());
                }
            }
        }
        applySelection();
    }

    public void clearSelection() {
        selected.clear();
        applySelection();
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    /** Clears the green/red review colouring while keeping the current selection. */
    public void clearReveal() {
        locked = false;
        for (Row row : rows) {
            row.mark.setVisibility(GONE);
            row.mark.setImageDrawable(null);
            row.view.setBackgroundResource(R.drawable.bg_option);
            row.letterView.setBackgroundResource(R.drawable.bg_letter);
            row.letterView.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
        }
        applySelection();
    }

    private void applySelection() {
        for (Row row : rows) {
            boolean on = selected.contains(row.letter);
            row.view.setBackgroundResource(on
                    ? R.drawable.bg_option_selected : R.drawable.bg_option);
            row.letterView.setBackgroundResource(R.drawable.bg_letter);
            row.letterView.setTextColor(ContextCompat.getColor(getContext(),
                    on ? R.color.brand : R.color.text_primary));
        }
    }

    /** Marks the correct letters green and, if given, the user's wrong picks red. */
    public void revealAnswer(String correctLetters, @Nullable String userLetters) {
        locked = true;
        Set<String> correct = lettersOf(correctLetters);
        Set<String> user = lettersOf(userLetters);
        for (Row row : rows) {
            boolean isCorrect = correct.contains(row.letter);
            boolean userPicked = user.contains(row.letter);
            row.mark.setVisibility(VISIBLE);
            if (isCorrect) {
                row.view.setBackgroundResource(R.drawable.bg_option_correct);
                row.letterView.setBackgroundResource(R.drawable.bg_letter);
                row.letterView.setTextColor(ContextCompat.getColor(getContext(), R.color.success));
                row.mark.setImageResource(R.drawable.ic_check_circle);
            } else if (userPicked) {
                row.view.setBackgroundResource(R.drawable.bg_option_wrong);
                row.letterView.setTextColor(ContextCompat.getColor(getContext(), R.color.danger));
                row.mark.setImageResource(R.drawable.ic_close_circle);
            } else {
                row.view.setBackgroundResource(R.drawable.bg_option);
                row.letterView.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
                row.mark.setVisibility(GONE);
            }
        }
    }

    private static Set<String> lettersOf(@Nullable String raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw == null) {
            return out;
        }
        for (char c : raw.toUpperCase().toCharArray()) {
            if (c >= 'A' && c <= 'H') {
                out.add(String.valueOf(c));
            }
        }
        return out;
    }

    private static class Row {
        final String letter;
        final View view;
        final TextView letterView;
        final ImageView mark;

        Row(String letter, View view, TextView letterView, ImageView mark) {
            this.letter = letter;
            this.view = view;
            this.letterView = letterView;
            this.mark = mark;
        }
    }
}
