package com.example.marketplace.util;

import java.util.concurrent.TimeUnit;

public final class TimeFormat {
    private TimeFormat() {
    }

    public static String formatDuration(long millis) {
        if (millis <= 0) {
            return "0s";
        }

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;

        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            builder.append(hours).append("h ");
        }
        builder.append(minutes).append("m");
        return builder.toString().trim();
    }

    public static String formatAge(long timestamp) {
        return formatDuration(System.currentTimeMillis() - timestamp);
    }
}
