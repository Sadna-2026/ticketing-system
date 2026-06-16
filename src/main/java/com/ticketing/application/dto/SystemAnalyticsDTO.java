package com.ticketing.application.dto;

import java.time.Instant;

import com.ticketing.application.services.SystemAnalyticsCollector.MetricSnapshot;
import com.ticketing.application.services.SystemAnalyticsCollector.RateSnapshot;
import com.ticketing.application.services.SystemAnalyticsCollector.Snapshot;

/**
 * Admin-facing system analytics (II.6.5 / UI-41).
 */
public record SystemAnalyticsDTO(
        Instant generatedAt,
        int activeVisitors,
        String liveWindowLabel,
        String historicalWindowLabel,
        AnalyticsMetricsDTO live,
        AnalyticsMetricsDTO historical) {

    public static SystemAnalyticsDTO from(Snapshot snapshot) {
        return new SystemAnalyticsDTO(
                snapshot.generatedAt(),
                snapshot.activeVisitors(),
                formatWindow(snapshot.liveWindow()),
                formatWindow(snapshot.historicalWindow()),
                AnalyticsMetricsDTO.from(snapshot.live()),
                AnalyticsMetricsDTO.from(snapshot.historical()));
    }

    private static String formatWindow(java.time.Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes < 1) {
            return "last " + Math.max(1, duration.toSeconds()) + " second(s)";
        }
        if (minutes < 120) {
            return "last " + minutes + " minute(s)";
        }
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        if (remainingMinutes == 0) {
            return "last " + hours + " hour(s)";
        }
        return "last " + hours + "h " + remainingMinutes + "m";
    }

    public record AnalyticsMetricsDTO(
            AnalyticsRateDTO visitorEnter,
            AnalyticsRateDTO visitorExit,
            AnalyticsRateDTO registration,
            AnalyticsRateDTO reservation,
            AnalyticsRateDTO purchase) {

        static AnalyticsMetricsDTO from(MetricSnapshot snapshot) {
            return new AnalyticsMetricsDTO(
                    AnalyticsRateDTO.from(snapshot.visitorEnter()),
                    AnalyticsRateDTO.from(snapshot.visitorExit()),
                    AnalyticsRateDTO.from(snapshot.registration()),
                    AnalyticsRateDTO.from(snapshot.reservation()),
                    AnalyticsRateDTO.from(snapshot.purchase()));
        }
    }

    public record AnalyticsRateDTO(long count, double perMinute) {
        static AnalyticsRateDTO from(RateSnapshot snapshot) {
            return new AnalyticsRateDTO(snapshot.count(), snapshot.perMinute());
        }
    }
}
