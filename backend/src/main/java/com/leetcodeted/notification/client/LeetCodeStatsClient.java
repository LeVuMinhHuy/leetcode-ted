package com.leetcodeted.notification.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    private static final String PROFILE_QUERY = """
            query getUserProfile($username: String!) {
              matchedUser(username: $username) {
                username
                profile {
                  realName
                  userAvatar
                  countryName
                }
                submitStatsGlobal {
                  acSubmissionNum {
                    difficulty
                    count
                  }
                }
              }
              allQuestionsCount {
                difficulty
                count
              }
            }""";

    private static final String CONTEST_QUERY = """
            query userContestRankingInfo($username: String!) {
              userContestRanking(username: $username) {
                rating
                globalRanking
                topPercentage
                attendedContestsCount
              }
            }""";

    private static final String CALENDAR_QUERY = """
            query userProfileCalendar($username: String!, $year: Int) {
              matchedUser(username: $username) {
                userCalendar(year: $year) {
                  activeYears
                  streak
                  totalActiveDays
                  submissionCalendar
                }
              }
            }""";

    private static final String USER_EXISTS_QUERY = """
            query userExists($username: String!) {
              matchedUser(username: $username) {
                username
              }
            }""";

    @Value("${app.leetcode.stats-api}")
    private String statsApiBaseUrl;

    private RestClient restClient;
    private RestClient graphqlClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    // ── Board snapshot data (GraphQL) ────────────────────────────────────────────

    public boolean userExists(String username) {
        try {
            var raw = graphql(USER_EXISTS_QUERY, Map.of("username", username));
            return raw != null && raw.get("matchedUser") != null;
        } catch (Exception e) {
            log.warn("userExists check failed for '{}': {}", username, e.getMessage());
            return false;
        }
    }

    public Optional<UserProfile> fetchProfile(String username) {
        try {
            var raw = graphql(PROFILE_QUERY, Map.of("username", username));
            if (raw == null) return Optional.empty();

            @SuppressWarnings("unchecked")
            var matchedUser = (Map<String, Object>) raw.get("matchedUser");
            if (matchedUser == null) return Optional.empty();

            @SuppressWarnings("unchecked")
            var profile = (Map<String, Object>) matchedUser.get("profile");
            String realName = profile != null ? (String) profile.get("realName") : null;
            String avatar = profile != null ? (String) profile.get("userAvatar") : null;
            String country = profile != null ? (String) profile.get("countryName") : null;

            int easy = 0, medium = 0, hard = 0;
            @SuppressWarnings("unchecked")
            var statsGlobal = (Map<String, Object>) matchedUser.get("submitStatsGlobal");
            if (statsGlobal != null) {
                @SuppressWarnings("unchecked")
                var acSubmissionNum = (List<Map<String, Object>>) statsGlobal.get("acSubmissionNum");
                if (acSubmissionNum != null) {
                    for (var entry : acSubmissionNum) {
                        String diff = (String) entry.get("difficulty");
                        int count = ((Number) entry.get("count")).intValue();
                        switch (diff) {
                            case "Easy" -> easy = count;
                            case "Medium" -> medium = count;
                            case "Hard" -> hard = count;
                        }
                    }
                }
            }

            int totalQuestions = 0;
            @SuppressWarnings("unchecked")
            var allQ = (List<Map<String, Object>>) raw.get("allQuestionsCount");
            if (allQ != null) {
                for (var entry : allQ) {
                    if ("All".equals(entry.get("difficulty"))) {
                        totalQuestions = ((Number) entry.get("count")).intValue();
                    }
                }
            }

            int totalSolved = easy + medium + hard;
            double acceptanceRate = totalQuestions > 0 ? (double) totalSolved / totalQuestions * 100 : 0;

            return Optional.of(new UserProfile(
                    username, realName, avatar, country,
                    totalSolved, totalQuestions, easy, medium, hard, acceptanceRate));
        } catch (Exception e) {
            log.warn("Failed to fetch profile for '{}': {}", username, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<ContestInfo> fetchContestInfo(String username) {
        try {
            var raw = graphql(CONTEST_QUERY, Map.of("username", username));
            if (raw == null) return Optional.empty();

            @SuppressWarnings("unchecked")
            var ranking = (Map<String, Object>) raw.get("userContestRanking");
            if (ranking == null) return Optional.of(ContestInfo.EMPTY);

            Double rating = ranking.get("rating") != null ? ((Number) ranking.get("rating")).doubleValue() : null;
            Integer globalRanking = ranking.get("globalRanking") != null ? ((Number) ranking.get("globalRanking")).intValue() : null;
            Integer count = ranking.get("attendedContestsCount") != null ? ((Number) ranking.get("attendedContestsCount")).intValue() : null;

            return Optional.of(new ContestInfo(rating, null, globalRanking, count));
        } catch (Exception e) {
            log.warn("Failed to fetch contest info for '{}': {}", username, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<CalendarData> fetchCalendar(String username, Integer year) {
        try {
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("username", username);
            if (year != null) vars.put("year", year);

            var raw = graphql(CALENDAR_QUERY, vars);
            if (raw == null) return Optional.empty();

            @SuppressWarnings("unchecked")
            var matchedUser = (Map<String, Object>) raw.get("matchedUser");
            if (matchedUser == null) return Optional.empty();

            @SuppressWarnings("unchecked")
            var cal = (Map<String, Object>) matchedUser.get("userCalendar");
            if (cal == null) return Optional.empty();

            @SuppressWarnings("unchecked")
            List<Integer> activeYears = cal.get("activeYears") != null
                    ? ((List<Number>) cal.get("activeYears")).stream().map(Number::intValue).toList()
                    : List.of();

            int streak = cal.get("streak") != null ? ((Number) cal.get("streak")).intValue() : 0;
            int totalActiveDays = cal.get("totalActiveDays") != null ? ((Number) cal.get("totalActiveDays")).intValue() : 0;

            String calendarJson = (String) cal.get("submissionCalendar");
            Map<Long, Integer> submissionCalendar = Collections.emptyMap();
            if (calendarJson != null && !calendarJson.isBlank()) {
                Map<String, Integer> parsed = objectMapper.readValue(calendarJson, new TypeReference<>() {});
                submissionCalendar = new LinkedHashMap<>();
                for (var entry : parsed.entrySet()) {
                    submissionCalendar.put(Long.parseLong(entry.getKey()), entry.getValue());
                }
            }

            return Optional.of(new CalendarData(activeYears, streak, totalActiveDays, submissionCalendar));
        } catch (Exception e) {
            log.warn("Failed to fetch calendar for '{}': {}", username, e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> graphql(String query, Map<String, Object> variables) {
        Map<String, Object> response = graphqlClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query", query, "variables", variables))
                .retrieve()
                .body(Map.class);
        if (response == null) return null;
        return (Map<String, Object>) response.get("data");
    }

    // ── Records ──────────────────────────────────────────────────────────────────

    public record UserProfile(
            String username, String displayName, String avatar, String country,
            int totalSolved, int totalQuestions, int easy, int medium, int hard,
            double acceptanceRate) {}

    public record ContestInfo(Double rating, Double maxRating, Integer globalRanking, Integer contestCount) {
        public static final ContestInfo EMPTY = new ContestInfo(null, null, null, null);
    }

    public record CalendarData(
            List<Integer> activeYears, int streak, int totalActiveDays,
            Map<Long, Integer> submissionCalendar) {}

    // ── Existing poller methods ──────────────────────────────────────────────────

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
