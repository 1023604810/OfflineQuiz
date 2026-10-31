package com.example.offlinequiz.ui;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.offlinequiz.R;
import com.example.offlinequiz.databinding.ActivityMainBinding;
import com.example.offlinequiz.ui.exam.ExamFragment;
import com.example.offlinequiz.ui.favorite.FavoriteFragment;
import com.example.offlinequiz.ui.practice.PracticeFragment;
import com.example.offlinequiz.ui.stats.StatsFragment;
import com.example.offlinequiz.ui.wrong.WrongFragment;
import com.google.android.material.navigation.NavigationBarView;

/** Shell hosting the five tabs: 练习 / 模考 / 错题 / 收藏 / 统计. */
public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_TAB = "tab";
    public static final int TAB_PRACTICE = 0;
    public static final int TAB_EXAM = 1;
    public static final int TAB_WRONG = 2;
    public static final int TAB_FAVORITE = 3;
    public static final int TAB_STATS = 4;

    private ActivityMainBinding binding;
    private int currentTab = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.bottomNav.setOnItemSelectedListener(this::onNavSelected);

        int tab = getIntent().getIntExtra(EXTRA_TAB, TAB_PRACTICE);
        showTab(tab);
    }

    private boolean onNavSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.tab_practice) {
            showTab(TAB_PRACTICE);
        } else if (id == R.id.tab_exam) {
            showTab(TAB_EXAM);
        } else if (id == R.id.tab_wrong) {
            showTab(TAB_WRONG);
        } else if (id == R.id.tab_favorite) {
            showTab(TAB_FAVORITE);
        } else if (id == R.id.tab_stats) {
            showTab(TAB_STATS);
        }
        return true;
    }

    private void showTab(int tab) {
        if (tab == currentTab) {
            return;
        }
        currentTab = tab;
        Fragment fragment;
        switch (tab) {
            case TAB_EXAM:
                fragment = new ExamFragment();
                break;
            case TAB_WRONG:
                fragment = new WrongFragment();
                break;
            case TAB_FAVORITE:
                fragment = new FavoriteFragment();
                break;
            case TAB_STATS:
                fragment = new StatsFragment();
                break;
            default:
                fragment = new PracticeFragment();
                break;
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commitNow();
        binding.bottomNav.getMenu().getItem(tab).setChecked(true);
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        showTab(intent.getIntExtra(EXTRA_TAB, TAB_PRACTICE));
    }
}
