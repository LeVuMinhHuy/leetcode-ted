package com.leetcodeted.notification.scheduler;

import com.leetcodeted.notification.client.LeetCodeStatsClient;
import com.leetcodeted.notification.client.LeetCodeStatsClient.RecentSubmission;
import com.leetcodeted.notification.model.BoardMember;
import com.leetcodeted.notification.repository.BoardMemberRepository;
import com.leetcodeted.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects new Accepted submissions on LeetCode for every board member and publishes
 * {@code member_solved}, at most once per member per problem per day.
 *
 * <p>Each member's {@code lastSeenSubmissionTs} is a watermark. The first poll for a
 * member only seeds it, so enabling the feature doesn't replay old solves.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SolvedSubmissionPoller {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    // Skips the backlog after downtime or after the flag was off for a while.
    private static final Duration MAX_NOTIFY_AGE = Duration.ofMinutes(30);

    private final BoardMemberRepository boardMemberRepository;
    private final LeetCodeStatsClient leetCodeStatsClient;
    private final NotificationService notificationService;
    private final MongoTemplate mongoTemplate;

    // fixedDelay, not fixedRate: the next poll starts only after this one finishes.
    @Scheduled(fixedDelayString = "${app.solve-poll.interval-ms:10000}")
    public void poll() {
        if (!notificationService.isEnabled()) {
            return;
        }
        for (BoardMember member : boardMemberRepository.findAll()) {
            try {
                leetCodeStatsClient.fetchRecentSubmissions(member.getUsername())
                        .ifPresent(submissions -> process(member, submissions));
            } catch (Exception e) {
                log.error("Solve poll failed for '{}': {}", member.getUsername(), e.getMessage(), e);
            }
        }
    }

    void process(BoardMember member, List<RecentSubmission> submissions) {
        long nowEpoch = Instant.now().getEpochSecond();
        long newestTs = submissions.stream().mapToLong(RecentSubmission::timestamp).max().orElse(0L);
        Long watermark = member.getLastSeenSubmissionTs();

        if (watermark == null) {
            saveWatermark(member.getUsername(), Math.max(newestTs, nowEpoch));
            log.info("Seeded solve watermark for '{}'", member.getUsername());
            return;
        }
        if (newestTs <= watermark) {
            return;
        }

        String today = LocalDate.now(VN_ZONE).toString();
        Set<String> notifiedSlugs = new LinkedHashSet<>();
        if (today.equals(member.getNotifiedDate()) && member.getNotifiedSlugs() != null) {
            notifiedSlugs.addAll(member.getNotifiedSlugs());
        }

        long oldestAllowed = nowEpoch - MAX_NOTIFY_AGE.toSeconds();
        List<RecentSubmission> fresh = submissions.stream()
                .filter(s -> s.timestamp() > watermark && s.timestamp() >= oldestAllowed && s.accepted())
                .sorted(Comparator.comparingLong(RecentSubmission::timestamp))
                .toList();

        for (RecentSubmission submission : fresh) {
            if (notifiedSlugs.add(submission.titleSlug())) {
                notificationService.publishMemberSolved(member, submission);
                log.info("member_solved: '{}' solved '{}'", member.getUsername(), submission.titleSlug());
            }
        }

        mongoTemplate.updateFirst(
                byUsername(member.getUsername()),
                new Update()
                        .set("lastSeenSubmissionTs", newestTs)
                        .set("notifiedDate", today)
                        .set("notifiedSlugs", new ArrayList<>(notifiedSlugs)),
                BoardMember.class);
    }

    private void saveWatermark(String username, long ts) {
        mongoTemplate.updateFirst(byUsername(username), new Update().set("lastSeenSubmissionTs", ts), BoardMember.class);
    }

    private static Query byUsername(String username) {
        return Query.query(Criteria.where("username").is(username));
    }
}
