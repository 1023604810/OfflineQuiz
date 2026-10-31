package com.example.offlinequiz.data.model;

/** One day of practice volume, used by the statistics chart. */
public class DayStat {
    public long dayStart;
    public String label;
    public int total;
    public int correct;

    public int accuracy() {
        return total == 0 ? 0 : Math.round(correct * 100f / total);
    }
}
