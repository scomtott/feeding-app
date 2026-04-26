package com.example.springboot.homeassistant.services;

import java.time.LocalTime;
import java.util.List;

public final class TimeOfDayBrightnessScheduleService {

    private static final LocalTime SEVEN_AM = LocalTime.of(7, 0);
    private static final LocalTime EIGHT_AM = LocalTime.of(8, 0);
    private static final LocalTime SIX_PM = LocalTime.of(18, 0);
    private static final LocalTime NINE_PM = LocalTime.of(21, 0);

    private static final double MORNING_STEEPNESS = 5.0;
    private static final double EVENING_STEEPNESS = 8.0;

    private TimeOfDayBrightnessScheduleService() {
    }

    public static int setBrightnessForTimeOfDay(int minBrightness, int maxBrightness) {
        return brightnessAt(LocalTime.now(), minBrightness, maxBrightness);
    }

    public static int brightnessAt(LocalTime now, int minBrightness, int maxBrightness) {
        int low = minBrightness;
        int high = maxBrightness;

        if (now.isBefore(SEVEN_AM)) {
            return low;
        }

        if (now.isBefore(EIGHT_AM)) {
            int startSecond = SEVEN_AM.toSecondOfDay();
            int endSecond = EIGHT_AM.toSecondOfDay();
            int nowSecond = now.toSecondOfDay();

            double progress = (double) (nowSecond - startSecond) / (endSecond - startSecond);
            double sigmoidAtStart = 1.0 / (1.0 + Math.exp(-MORNING_STEEPNESS * (0.0 - 0.5)));
            double sigmoidAtEnd = 1.0 / (1.0 + Math.exp(-MORNING_STEEPNESS * (1.0 - 0.5)));
            double sigmoidNow = 1.0 / (1.0 + Math.exp(-MORNING_STEEPNESS * (progress - 0.5)));

            double normalized = (sigmoidNow - sigmoidAtStart) / (sigmoidAtEnd - sigmoidAtStart);
            double brightness = low + (high - low) * normalized;
            return (int) Math.round(brightness);
        }

        if (now.isBefore(SIX_PM)) {
            return high;
        }

        if (!now.isBefore(NINE_PM)) {
            return low;
        }

        int startSecond = SIX_PM.toSecondOfDay();
        int endSecond = NINE_PM.toSecondOfDay();
        int nowSecond = now.toSecondOfDay();

        double progress = (double) (nowSecond - startSecond) / (endSecond - startSecond);

        double sigmoidAtStart = 1.0 / (1.0 + Math.exp(-EVENING_STEEPNESS * (0.0 - 0.5)));
        double sigmoidAtEnd = 1.0 / (1.0 + Math.exp(-EVENING_STEEPNESS * (1.0 - 0.5)));
        double sigmoidNow = 1.0 / (1.0 + Math.exp(-EVENING_STEEPNESS * (progress - 0.5)));

        double normalized = (sigmoidNow - sigmoidAtStart) / (sigmoidAtEnd - sigmoidAtStart);
        double brightness = high - (high - low) * normalized;

        return (int) Math.round(brightness);
    }

    public static SchedulePosition currentPosition(LocalTime now) {
        if (now.isBefore(SEVEN_AM)) {
            return new SchedulePosition("Night", 1.0);
        }

        if (now.isBefore(EIGHT_AM)) {
            double progress = phaseProgress(now, SEVEN_AM, EIGHT_AM);
            return new SchedulePosition("Morning ramp", progress);
        }

        if (now.isBefore(SIX_PM)) {
            return new SchedulePosition("Day", 1.0);
        }

        if (now.isBefore(NINE_PM)) {
            double progress = phaseProgress(now, SIX_PM, NINE_PM);
            return new SchedulePosition("Evening ramp", progress);
        }

        return new SchedulePosition("Night", 1.0);
    }

    public static List<ScheduleAnchor> anchors(int minBrightness, int maxBrightness) {
        return List.of(
            new ScheduleAnchor("00:00", "Night", minBrightness),
            new ScheduleAnchor("07:00", "Morning ramp starts", minBrightness),
            new ScheduleAnchor("08:00", "Day level reached", maxBrightness),
            new ScheduleAnchor("18:00", "Evening ramp starts", maxBrightness),
            new ScheduleAnchor("21:00", "Night level reached", minBrightness)
        );
    }

    public static List<SchedulePoint> points(int minBrightness, int maxBrightness, int intervalMinutes) {
        int clampedIntervalMinutes = Math.max(1, intervalMinutes);
        int intervalSeconds = clampedIntervalMinutes * 60;

        java.util.ArrayList<SchedulePoint> points = new java.util.ArrayList<>();
        for (int secondOfDay = 0; secondOfDay <= 24 * 60 * 60; secondOfDay += intervalSeconds) {
            int normalizedSecondOfDay = Math.min(secondOfDay, 24 * 60 * 60 - 1);
            LocalTime at = LocalTime.ofSecondOfDay(normalizedSecondOfDay);
            int brightness = brightnessAt(at, minBrightness, maxBrightness);
            points.add(new SchedulePoint(normalizedSecondOfDay, at.toString(), brightness));
        }

        return points;
    }

    private static double phaseProgress(LocalTime now, LocalTime start, LocalTime end) {
        int nowSecond = now.toSecondOfDay();
        int startSecond = start.toSecondOfDay();
        int endSecond = end.toSecondOfDay();
        if (endSecond <= startSecond) {
            return 1.0;
        }
        double progress = (double) (nowSecond - startSecond) / (endSecond - startSecond);
        return Math.max(0.0, Math.min(1.0, progress));
    }

    public record SchedulePosition(String phase, double progress) {
    }

    public record ScheduleAnchor(String time, String label, int brightness) {
    }

    public record SchedulePoint(int secondOfDay, String time, int brightness) {
    }
}
