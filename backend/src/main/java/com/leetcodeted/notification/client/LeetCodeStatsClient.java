package com.leetcodeted.notification.client;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * HTTP client that queries the LeetCode stats API to check whether a member
 * has solved at least one problem today (Vietnam timezone).
 *
 * <p>Thread-safe: {@link RestClient} is immutable after construction.
 */
@Slf4j
@Component
public class LeetCodeStatsClient {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Value("${app.leetcode.stats-api}")
    private String statsApiBaseUrl;

    private RestClient restClient;

    @PostConstruct
    void init() {
        restClient = RestClient.builder()
                .baseUrl(statsApiBaseUrl)
                .build();
    }

    /**
     * Returns {@code true} if {@code username} has at least one submission today
     * (Asia/Ho_Chi_Minh), {@code false} on failure or no submission.
     */
    public boolean hasSolvedToday(String username) {
        String today = LocalDate.now(VN_ZONE).toString(); // "yyyy-MM-dd"
        try {
            HeatmapEnvelope envelope = restClient.get()
                    .uri("/{username}/heatmap", username)
                    .retrieve()
                    .body(HeatmapEnvelope.class);

            if (envelope == null || !"success".equals(envelope.status()) || envelope.data() == null) {
                log.warn("LeetCode stats API returned unexpected response for user '{}'", username);
                return false;
            }

            List<DailyEntry> daily = envelope.data().dailyContributions();
            if (daily == null) {
                return false;
            }

            return daily.stream()
                    .anyMatch(e -> today.equals(e.date()) && e.count() > 0);

        } catch (Exception ex) {
            log.warn("Failed to fetch LeetCode heatmap for user '{}': {}", username, ex.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Private DTOs — mapped from the heatmap API response
    // -------------------------------------------------------------------------

    private record HeatmapEnvelope(String status, HeatmapData data) {}

    private record HeatmapData(List<DailyEntry> dailyContributions) {}

    private record DailyEntry(String date, int count) {}
}
