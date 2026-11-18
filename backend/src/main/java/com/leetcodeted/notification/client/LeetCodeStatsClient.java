package com.leetcodeted.notification.client;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads member activity: recent submissions straight from LeetCode GraphQL, and the
 * streak count from the third-party stats API.
 *
 * <p>Thread-safe: {@link RestClient} is immutable after construction.
 */
@Slf4j
@Component
public class LeetCodeStatsClient {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final String LEETCODE_GRAPHQL_URL = "https://leetcode.com/graphql";

    private static final String RECENT_SUBMISSIONS_QUERY = """
            query recentSubmissions($username: String!) {
              recentSubmissionList(username: $username, limit: 20) {
                title titleSlug timestamp statusDisplay
              }
            }""";

    @Value("${app.leetcode.stats-api}")
    private String statsApiBaseUrl;

    private RestClient restClient;

    // The stats API caches LeetCode data server-side for hours, so solve detection
    // must query LeetCode directly.
    private RestClient graphqlClient;

    @PostConstruct
    void init() {
        restClient = RestClient.builder()
                .baseUrl(statsApiBaseUrl)
                .build();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        graphqlClient = RestClient.builder()
                .baseUrl(LEETCODE_GRAPHQL_URL)
                .requestFactory(requestFactory)
                .defaultHeader("Referer", "https://leetcode.com")
                .build();
    }

    /**
     * Returns {@code true} if {@code username} has at least one submission today
     * (Asia/Ho_Chi_Minh), {@code false} on failure or no submission.
     */
    public boolean hasSolvedToday(String username) {
        LocalDate today = LocalDate.now(VN_ZONE);
        return fetchRecentSubmissions(username)
                .map(submissions -> submissions.stream().anyMatch(s ->
                        Instant.ofEpochSecond(s.timestamp()).atZone(VN_ZONE).toLocalDate().equals(today)))
                .orElse(false);
    }

    /**
     * Returns the member's most recent submissions (newest first, ~20 max),
     * or empty if the API call fails — callers must not treat failure as "no submissions".
     */
    public Optional<List<RecentSubmission>> fetchRecentSubmissions(String username) {
        try {
            GraphQlEnvelope envelope = graphqlClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", RECENT_SUBMISSIONS_QUERY, "variables", Map.of("username", username)))
                    .retrieve()
                    .body(GraphQlEnvelope.class);

            if (envelope == null || envelope.data() == null || envelope.data().recentSubmissionList() == null) {
                log.warn("LeetCode GraphQL returned no recentSubmissionList for user '{}'", username);
                return Optional.empty();
            }
            return Optional.of(envelope.data().recentSubmissionList());
        } catch (Exception ex) {
            log.warn("Failed to fetch recent submissions for user '{}': {}", username, ex.getMessage());
            return Optional.empty();
        }
    }

    /** {@code timestamp} is epoch seconds; the API sends it as a string. */
    public record RecentSubmission(String title, String titleSlug, long timestamp, String statusDisplay) {
        public boolean accepted() {
            return "Accepted".equals(statusDisplay);
        }

        /** LeetCode lists a submission with an empty status for a second or so while judging. */
        public boolean judging() {
            return statusDisplay == null || statusDisplay.isBlank();
        }
    }

    private record GraphQlEnvelope(RecentSubmissionData data) {}

    private record RecentSubmissionData(List<RecentSubmission> recentSubmissionList) {}

    /** Returns the member's current daily solve streak, or 0 on failure. */
    public int fetchCurrentStreak(String username) {
        try {
            HeatmapEnvelope envelope = restClient.get()
                    .uri("/{username}/heatmap", username)
                    .retrieve()
                    .body(HeatmapEnvelope.class);
            if (envelope == null || !"success".equals(envelope.status()) || envelope.data() == null) {
                log.warn("Unexpected heatmap response for user '{}' while fetching streak", username);
                return 0;
            }
            return envelope.data().currentStreak();
        } catch (Exception ex) {
            log.warn("Failed to fetch streak for user '{}': {}", username, ex.getMessage());
            return 0;
        }
    }

    private record HeatmapEnvelope(String status, HeatmapData data) {}

    private record HeatmapData(List<DailyEntry> dailyContributions, int currentStreak) {}

    private record DailyEntry(String date, int count) {}
}
