package com.leetcodeted.notification.service;

import com.leetcodeted.notification.client.LeetCodeStatsClient;
import com.leetcodeted.notification.client.LeetCodeStatsClient.CalendarData;
import com.leetcodeted.notification.client.LeetCodeStatsClient.ContestInfo;
import com.leetcodeted.notification.client.LeetCodeStatsClient.RecentSubmission;
import com.leetcodeted.notification.client.LeetCodeStatsClient.UserProfile;
import com.leetcodeted.notification.model.BoardMember;
import com.leetcodeted.notification.repository.BoardMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoardService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    // Excused dates per username, matching the frontend constant
    private static final Map<String, Set<String>> EXCUSED_DATES = Map.of(
            "jaydennguy2211", Set.of("2026-10-16"),
            "n_t_hien126", Set.of("2026-10-16")
    );

    private final BoardMemberRepository boardMemberRepository;
    private final LeetCodeStatsClient leetCodeClient;
    private final MongoTemplate mongoTemplate;

    // Simple TTL cache: snapshot list + expiry
    private volatile List<MemberSnapshotDto> cachedBoard;
    private volatile long cacheExpiresAt;
    private static final long CACHE_TTL_MS = 60_000; // 60s

    public List<MemberSnapshotDto> getBoard() {
        long now = System.currentTimeMillis();
        List<MemberSnapshotDto> cached = this.cachedBoard;
        if (cached != null && now < cacheExpiresAt) {
            return cached;
        }
        List<MemberSnapshotDto> fresh = buildBoard();
        this.cachedBoard = fresh;
        this.cacheExpiresAt = now + CACHE_TTL_MS;
        return fresh;
    }

    public void invalidateCache() {
        this.cacheExpiresAt = 0;
    }

    private List<MemberSnapshotDto> buildBoard() {
        List<BoardMember> members = boardMemberRepository.findAll();
        // Hardcoded seed member (matches frontend BOARD_MEMBERS constant)
        boolean hasTed = members.stream().anyMatch(m -> "tedvu".equals(m.getUsername()));
        if (!hasTed) {
            BoardMember ted = new BoardMember();
            ted.setUsername("tedvu");
            ted.setDisplayName("Ted");
            members = new ArrayList<>(members);
            members.add(ted);
        }

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Future<MemberSnapshotDto>> futures = members.stream()
                    .map(member -> executor.submit(() -> buildSnapshot(member)))
                    .toList();

            List<MemberSnapshotDto> snapshots = new ArrayList<>();
            for (Future<MemberSnapshotDto> f : futures) {
                try {
                    snapshots.add(f.get(15, TimeUnit.SECONDS));
                } catch (Exception e) {
                    log.error("Failed to build snapshot: {}", e.getMessage());
                }
            }
            snapshots.sort(Comparator.comparingInt(MemberSnapshotDto::elo).reversed()
                    .thenComparingInt(MemberSnapshotDto::currentStreak).reversed()
                    .thenComparingInt(MemberSnapshotDto::totalSolved).reversed());
            return snapshots;
        } finally {
            executor.shutdown();
        }
    }

    private MemberSnapshotDto buildSnapshot(BoardMember member) {
        String username = member.getUsername();
        UserProfile profile = leetCodeClient.fetchProfile(username).orElse(null);
        ContestInfo contest = leetCodeClient.fetchContestInfo(username).orElse(ContestInfo.EMPTY);
        CalendarData calendar = leetCodeClient.fetchCalendar(username, null).orElse(null);
        List<RecentSubmission> recent = leetCodeClient.fetchRecentSubmissions(username).orElse(List.of());

        int easy = 0, medium = 0, hard = 0, totalSolved = 0, totalQuestions = 0;
        double acceptanceRate = 0;
        String displayName = member.getDisplayName();
        String avatar = null;
        String country = null;

        if (profile != null) {
            easy = profile.easy();
            medium = profile.medium();
            hard = profile.hard();
            totalSolved = profile.totalSolved();
            totalQuestions = profile.totalQuestions();
            acceptanceRate = profile.acceptanceRate();
            if (displayName == null || displayName.isBlank()) {
                displayName = profile.displayName();
            }
            avatar = profile.avatar();
            country = profile.country();
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = username;
        }

        // Streak from calendar
        int currentStreak = 0, longestStreak = 0;
        int year = java.time.Year.now().getValue();
        List<DailyContributionDto> contributions = List.of();
        List<Integer> availableYears = List.of(year);

        if (calendar != null) {
            Set<String> excused = EXCUSED_DATES.getOrDefault(username, Set.of());
            var streakResult = StreakCalculator.compute(calendar.submissionCalendar(), excused, System.currentTimeMillis());
            currentStreak = streakResult.currentStreak();
            longestStreak = streakResult.longestStreak();

            if (!calendar.activeYears().isEmpty()) {
                availableYears = calendar.activeYears();
                year = pickDefaultYear(calendar);
            }
            contributions = buildContributions(calendar.submissionCalendar(), year);
        }

        int elo = EloCalculator.compute(easy, medium, hard, currentStreak);

        // Today's submissions (VN timezone)
        long todayStartEpoch = java.time.LocalDate.now(VN_ZONE).atStartOfDay(VN_ZONE).toEpochSecond();
        Map<String, RecentSubmissionDto> todayMap = new LinkedHashMap<>();
        recent.stream()
                .filter(s -> s.timestamp() >= todayStartEpoch)
                .sorted(Comparator.comparingLong(RecentSubmission::timestamp).reversed())
                .forEach(s -> {
                    String slug = s.titleSlug();
                    RecentSubmissionDto existing = todayMap.get(slug);
                    String status = s.accepted() || (existing != null && "Accepted".equals(existing.status()))
                            ? "Accepted" : "In progress";
                    todayMap.put(slug, new RecentSubmissionDto(
                            s.title(),
                            "https://leetcode.com/problems/" + slug + "/",
                            status));
                });

        return new MemberSnapshotDto(
                username, displayName, avatar, country,
                totalSolved, totalQuestions, easy, medium, hard, acceptanceRate,
                contest.rating(), contest.maxRating(),
                contest.contestCount() != null ? String.valueOf(contest.contestCount()) : null,
                contest.globalRanking(),
                elo, currentStreak, longestStreak,
                year, contributions, availableYears,
                null, // no error
                new ArrayList<>(todayMap.values()));
    }

    private int pickDefaultYear(CalendarData calendar) {
        int currentYear = java.time.Year.now().getValue();
        if (calendar.activeYears().contains(currentYear)) return currentYear;
        return calendar.activeYears().stream().max(Integer::compareTo).orElse(currentYear);
    }

    private List<DailyContributionDto> buildContributions(Map<Long, Integer> calendarMap, int year) {
        List<DailyContributionDto> result = new ArrayList<>();
        for (var entry : calendarMap.entrySet()) {
            if (entry.getValue() <= 0) continue;
            String date = Instant.ofEpochSecond(entry.getKey()).atZone(VN_ZONE).toLocalDate().toString();
            if (date.startsWith(String.valueOf(year))) {
                result.add(new DailyContributionDto(date, entry.getValue()));
            }
        }
        result.sort(Comparator.comparing(DailyContributionDto::date));
        return result;
    }

    // ── Heatmap for a specific year ──────────────────────────────────────────────

    public Optional<YearHeatmapDto> getYearHeatmap(String username, int year) {
        return leetCodeClient.fetchCalendar(username, year)
                .map(cal -> {
                    Set<String> excused = EXCUSED_DATES.getOrDefault(username, Set.of());
                    var streak = StreakCalculator.compute(cal.submissionCalendar(), excused, System.currentTimeMillis());
                    return new YearHeatmapDto(
                            year,
                            buildContributions(cal.submissionCalendar(), year),
                            streak.currentStreak(),
                            streak.longestStreak(),
                            cal.activeYears());
                });
    }

    // ── Join board ───────────────────────────────────────────────────────────────

    public BoardMember joinBoard(String username, String displayName) {
        String normalized = username.trim().toLowerCase();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Username is required");

        if (!leetCodeClient.userExists(normalized)) {
            throw new NoSuchElementException("LeetCode username not found: " + normalized);
        }

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("username").is(normalized)),
                new Update()
                        .set("username", normalized)
                        .set("displayName", displayName != null && !displayName.isBlank() ? displayName.trim() : null)
                        .setOnInsert("joinedAt", Instant.now()),
                BoardMember.class);

        invalidateCache();

        BoardMember member = new BoardMember();
        member.setUsername(normalized);
        member.setDisplayName(displayName);
        return member;
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────────

    public record MemberSnapshotDto(
            String username, String displayName, String avatar, String country,
            int totalSolved, int totalQuestions, int easy, int medium, int hard, double acceptanceRate,
            Double contestRating, Double maxRating, String contestRank, Integer globalContestRanking,
            int elo, int currentStreak, int longestStreak,
            int year, List<DailyContributionDto> contributions, List<Integer> availableYears,
            String error, List<RecentSubmissionDto> recentSubmissions) {}

    public record DailyContributionDto(String date, int count) {}

    public record RecentSubmissionDto(String title, String url, String status) {}

    public record YearHeatmapDto(
            int year, List<DailyContributionDto> contributions,
            int currentStreak, int longestStreak, List<Integer> availableYears) {}
}
