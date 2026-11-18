package com.leetcodeted.notification.scheduler;

import com.leetcodeted.notification.client.LeetCodeStatsClient;
import com.leetcodeted.notification.model.BoardMember;
import com.leetcodeted.notification.repository.BoardMemberRepository;
import com.leetcodeted.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs daily at 14:00 UTC (21:00 ICT) and publishes a streak reminder for
 * board members who have not solved a problem today.
 *
 * <p>{@code hasSolvedToday} calls run sequentially to avoid bursting the
 * external LeetCode stats API; the board is small enough that this is fine.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreakReminderScheduler {

    private final BoardMemberRepository boardMemberRepository;
    private final LeetCodeStatsClient leetCodeStatsClient;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 14 * * *")
    public void sendStreakReminder() {
        List<BoardMember> members = boardMemberRepository.findAll().stream()
                .filter(m -> m.getEmail() != null)
                .toList();

        List<BoardMember> laggards = members.stream()
                .filter(m -> !leetCodeStatsClient.hasSolvedToday(m.getUsername()))
                .toList();

        notificationService.publishStreakReminder(laggards);

        log.info("Streak reminder: {} members checked, {} laggards notified",
                members.size(), laggards.size());
    }
}
