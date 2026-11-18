package com.leetcodeted.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetcodeted.notification.client.LeetCodeStatsClient.RecentSubmission;
import com.leetcodeted.notification.model.BoardMember;
import com.leetcodeted.notification.repository.BoardMemberRepository;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes domain events to AWS SQS when {@code app.notifications.enabled} is true.
 *
 * <p>All failures are swallowed and logged — notification delivery is best-effort.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    @Value("${app.notifications.enabled:false}")
    private boolean notificationsEnabled;

    @Value("${app.sqs.queue-url:}")
    private String queueUrl;

    private final SqsTemplate sqsTemplate;
    private final BoardMemberRepository boardMemberRepository;
    private final ObjectMapper objectMapper;

    public boolean isEnabled() {
        return notificationsEnabled;
    }

    public void publishMemberSolved(BoardMember solver, RecentSubmission submission) {
        try {
            List<Map<String, String>> recipients = boardMemberRepository.findAllByEmailIsNotNull().stream()
                    .filter(m -> !solver.getUsername().equals(m.getUsername()))
                    .map(m -> Map.of("username", m.getUsername(), "email", m.getEmail()))
                    .toList();

            if (recipients.isEmpty()) {
                log.debug("No recipients for member_solved (solver='{}'), skipping", solver.getUsername());
                return;
            }

            Map<String, Object> payload = Map.of(
                    "event", "member_solved",
                    "solver", Map.of("username", solver.getUsername(), "displayName", displayNameOf(solver)),
                    "problem", Map.of(
                            "title", submission.title(),
                            "titleSlug", submission.titleSlug(),
                            "url", "https://leetcode.com/problems/" + submission.titleSlug() + "/"),
                    "recipients", recipients
            );
            send("member_solved", payload);
        } catch (Exception e) {
            log.error("Failed to publish member_solved event: {}", e.getMessage(), e);
        }
    }

    public void publishStreakReminder(List<BoardMember> laggards) {
        if (laggards == null || laggards.isEmpty()) {
            log.debug("No laggards today, skipping streak_reminder");
            return;
        }
        try {
            List<Map<String, Object>> laggardDtos = laggards.stream()
                    .map(m -> {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("username", m.getUsername());
                        entry.put("displayName", displayNameOf(m));
                        entry.put("email", m.getEmail());
                        entry.put("currentStreak", m.getCurrentStreak() != null ? m.getCurrentStreak() : 0);
                        return entry;
                    })
                    .toList();

            Map<String, Object> payload = Map.of(
                    "event", "streak_reminder",
                    "sentAt", Instant.now().toString(),
                    "laggards", laggardDtos
            );
            send("streak_reminder", payload);
        } catch (Exception e) {
            log.error("Failed to publish streak_reminder event: {}", e.getMessage(), e);
        }
    }

    private void send(String event, Map<String, Object> payload) throws Exception {
        if (!notificationsEnabled) {
            log.debug("SQS notifications disabled, skipping {}", event);
            return;
        }
        if (queueUrl == null || queueUrl.isBlank()) {
            log.warn("app.sqs.queue-url is not configured, skipping {}", event);
            return;
        }
        sqsTemplate.send(queueUrl, objectMapper.writeValueAsString(payload));
        log.debug("Published {}", event);
    }

    private static String displayNameOf(BoardMember member) {
        String name = member.getDisplayName();
        return name == null || name.isBlank() ? member.getUsername() : name;
    }
}
