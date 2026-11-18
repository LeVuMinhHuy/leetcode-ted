package com.leetcodeted.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.stream.Collectors;

/**
 * Publishes domain events to AWS SQS when {@code app.notifications.enabled} is true.
 *
 * <p>All failures are swallowed and logged at ERROR level — notification delivery
 * is best-effort and must never break the calling flow.
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

    /**
     * Publishes a {@code member_solved} event so other board members receive a nudge.
     *
     * @param solverUsername    LeetCode username of the member who just solved
     * @param solverDisplayName display name of the solver
     * @param problemId         LeetCode problem id that was solved
     */
    public void publishMemberSolved(String solverUsername, String solverDisplayName, int problemId) {
        if (!notificationsEnabled) {
            log.debug("SQS notifications disabled, skipping member_solved");
            return;
        }
        if (queueUrl == null || queueUrl.isBlank()) {
            log.warn("app.sqs.queue-url is not configured, skipping member_solved");
            return;
        }

        List<BoardMember> members = boardMemberRepository.findAllByEmailIsNotNull();
        List<Map<String, String>> recipients = members.stream()
                .filter(m -> !solverUsername.equals(m.getUsername()))
                .filter(m -> m.getEmail() != null)
                .map(m -> Map.of("username", m.getUsername(), "email", m.getEmail()))
                .collect(Collectors.toList());

        if (recipients.isEmpty()) {
            log.debug("No recipients for member_solved event (solver='{}'), skipping", solverUsername);
            return;
        }

        Map<String, Object> payload = Map.of(
                "event", "member_solved",
                "solver", Map.of("username", solverUsername, "displayName", solverDisplayName),
                "problemId", problemId,
                "recipients", recipients
        );

        try {
            String json = objectMapper.writeValueAsString(payload);
            sqsTemplate.send(queueUrl, json);
            log.debug("Published member_solved: solver='{}', problemId={}, recipients={}",
                    solverUsername, problemId, recipients.size());
        } catch (Exception e) {
            log.error("Failed to publish member_solved event: {}", e.getMessage(), e);
        }
    }

    /**
     * Publishes a {@code streak_reminder} event for members who have not solved today.
     *
     * <p>{@code currentStreak} is set to {@code 0} here — the email consumer
     * can look it up from the database if the actual value is needed.
     *
     * @param laggards board members who have not solved a problem today
     */
    public void publishStreakReminder(List<BoardMember> laggards) {
        if (!notificationsEnabled) {
            log.debug("SQS notifications disabled, skipping streak_reminder");
            return;
        }
        if (queueUrl == null || queueUrl.isBlank()) {
            log.warn("app.sqs.queue-url is not configured, skipping streak_reminder");
            return;
        }

        if (laggards == null || laggards.isEmpty()) {
            log.debug("No laggards today, skipping streak_reminder");
            return;
        }

        List<Map<String, Object>> laggardDtos = laggards.stream()
                .map(m -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("username", m.getUsername());
                    entry.put("displayName", m.getDisplayName());
                    entry.put("email", m.getEmail());
                    entry.put("currentStreak", 0);
                    return entry;
                })
                .collect(Collectors.toList());

        Map<String, Object> payload = Map.of(
                "event", "streak_reminder",
                "sentAt", Instant.now().toString(),
                "laggards", laggardDtos
        );

        try {
            String json = objectMapper.writeValueAsString(payload);
            sqsTemplate.send(queueUrl, json);
            log.debug("Published streak_reminder for {} laggard(s)", laggards.size());
        } catch (Exception e) {
            log.error("Failed to publish streak_reminder event: {}", e.getMessage(), e);
        }
    }
}
