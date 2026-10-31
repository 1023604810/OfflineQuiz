package com.example.offlinequiz.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Date helpers; every key is computed in the device's local time zone. */
public final class DateUtil {

    private static final String KEY_PATTERN = "yyyy-MM-dd";

    private DateUtil() {
    }

    public static String todayKey() {
        return keyOf(System.currentTimeMillis());
    }

    public static String keyOf(long timestamp) {
        return new SimpleDateFormat(KEY_PATTERN, Locale.US).format(new Date(timestamp));
    }

    /** Timestamp of 00:00 of the day that contains {@code timestamp}, shifted by {@code dayOffset}. */
    public static long startOfDay(long timestamp, int dayOffset) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timestamp);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_YEAR, dayOffset);
        return c.getTimeInMillis();
    }

    /** yyyyMMdd as an int, used as the daily-practice random seed. */
    public static long dailySeed(String dateKey) {
        try {
            return Long.parseLong(dateKey.replace("-", ""));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static String formatTime(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(timestamp));
    }

    public static String weekdayLabel(long timestamp) {
        String[] names = {"日", "一", "二", "三", "四", "五", "六"};
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timestamp);
        return "周" + names[c.get(Calendar.DAY_OF_WEEK) - 1];
    }

    public static String shortDay(long timestamp) {
        return new SimpleDateFormat("MM/dd", Locale.US).format(new Date(timestamp));
    }
}
