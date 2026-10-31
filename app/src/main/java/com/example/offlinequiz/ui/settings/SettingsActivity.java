package com.example.offlinequiz.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.offlinequiz.App;
import com.example.offlinequiz.R;
import com.example.offlinequiz.databinding.ActivitySettingsBinding;
import com.example.offlinequiz.ui.MainActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Daily-practice size and local data management. */
public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding b;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.btnBack.setOnClickListener(v -> finish());

        RadioButton checked;
        switch (App.get().prefs().dailyCount()) {
            case 5:
                checked = b.rb5;
                break;
            case 20:
                checked = b.rb20;
                break;
            case 50:
                checked = b.rb50;
                break;
            default:
                checked = b.rb10;
                break;
        }
        checked.setChecked(true);
        b.rgDailyCount.setOnCheckedChangeListener((group, id) -> {
            int count = id == R.id.rb_5 ? 5 : id == R.id.rb_20 ? 20 : id == R.id.rb_50 ? 50 : 10;
            App.get().prefs().setDailyCount(count);
        });

        b.btnClearRecords.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("清空做题记录")
                .setMessage("将删除做题记录、错题本、收藏、每日进度与模考记录，题库保留。此操作不可撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (d, w) -> clearRecords())
                .show());

        b.btnClearAll.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("清空全部数据")
                .setMessage("将删除题库以及全部学习数据，App 会回到首次使用的状态。此操作不可撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("全部清空", (d, w) -> clearAll())
                .show());
    }

    private void clearRecords() {
        App.get().io().execute(() -> {
            App.get().db().practiceDao().deleteAll();
            App.get().db().wrongDao().deleteAll();
            App.get().db().favoriteDao().deleteAll();
            App.get().db().dailyDao().deleteAll();
            App.get().db().examDao().deleteAll();
            App.get().runOnUiThread(() -> Toast.makeText(this, "做题记录已清空", Toast.LENGTH_SHORT).show());
        });
    }

    private void clearAll() {
        App.get().io().execute(() -> {
            App.get().db().practiceDao().deleteAll();
            App.get().db().wrongDao().deleteAll();
            App.get().db().favoriteDao().deleteAll();
            App.get().db().dailyDao().deleteAll();
            App.get().db().examDao().deleteAll();
            App.get().db().questionDao().deleteAll();
            App.get().db().configDao().deleteAll();
            App.get().runOnUiThread(() -> {
                Toast.makeText(this, "全部数据已清空", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        });
    }
}
