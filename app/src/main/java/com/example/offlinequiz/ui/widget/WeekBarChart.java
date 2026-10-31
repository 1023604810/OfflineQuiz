package com.example.offlinequiz.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.offlinequiz.R;
import com.example.offlinequiz.data.model.DayStat;

import java.util.ArrayList;
import java.util.List;

/** Small self-contained column chart for "last 7 days" volume - no chart library needed. */
public class WeekBarChart extends View {

    private final List<DayStat> data = new ArrayList<>();
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public WeekBarChart(Context context) {
        this(context, null);
    }

    public WeekBarChart(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        barPaint.setColor(ContextCompat.getColor(context, R.color.brand));
        emptyPaint.setColor(ContextCompat.getColor(context, R.color.divider));
        labelPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        labelPaint.setTextSize(11 * density);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setColor(ContextCompat.getColor(context, R.color.text_primary));
        valuePaint.setTextSize(11 * density);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        dividerPaint.setColor(ContextCompat.getColor(context, R.color.divider));
        dividerPaint.setStrokeWidth(1 * density);
        linePaint.setColor(ContextCompat.getColor(context, R.color.success));
        linePaint.setStrokeWidth(2 * density);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        pointPaint.setColor(ContextCompat.getColor(context, R.color.success));
        pointPaint.setStyle(Paint.Style.FILL);
    }

    public void setData(List<DayStat> stats) {
        data.clear();
        if (stats != null) {
            data.addAll(stats);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (data.isEmpty()) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float labelHeight = 20 * density;
        float valueHeight = 16 * density;
        float padding = 4 * density;
        float top = valueHeight;
        float bottom = getHeight() - labelHeight;
        float baseY = bottom;
        float available = bottom - top;

        int max = 1;
        for (DayStat d : data) {
            max = Math.max(max, d.total);
        }

        canvas.drawLine(0, baseY, getWidth(), baseY, dividerPaint);

        int count = data.size();
        float slot = (float) getWidth() / count;
        float barWidth = Math.min(slot * 0.46f, 26 * density);

        for (int i = 0; i < count; i++) {
            DayStat d = data.get(i);
            float centerX = slot * i + slot / 2f;
            float h = available * (d.total / (float) max);
            if (d.total > 0) {
                h = Math.max(h, 4 * density);
            }
            rect.set(centerX - barWidth / 2f, baseY - h, centerX + barWidth / 2f, baseY);
            canvas.drawRoundRect(rect, barWidth / 2f, barWidth / 2f,
                    d.total > 0 ? barPaint : emptyPaint);
            if (d.total > 0) {
                canvas.drawText(String.valueOf(d.total), centerX, baseY - h - 4 * density,
                        valuePaint);
            }
            canvas.drawText(d.label, centerX, getHeight() - padding, labelPaint);
        }

        // accuracy trend line on top of the bars (0-100% mapped to the same height)
        Path path = new Path();
        boolean started = false;
        for (int i = 0; i < count; i++) {
            DayStat d = data.get(i);
            if (d.total <= 0) {
                continue;
            }
            float centerX = slot * i + slot / 2f;
            float y = baseY - available * (d.accuracy() / 100f);
            if (!started) {
                path.moveTo(centerX, y);
                started = true;
            } else {
                path.lineTo(centerX, y);
            }
            canvas.drawCircle(centerX, y, 3 * density, pointPaint);
        }
        if (started) {
            canvas.drawPath(path, linePaint);
        }
    }
}
