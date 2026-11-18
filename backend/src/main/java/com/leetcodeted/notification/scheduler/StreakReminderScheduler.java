package com.leetcodeted.notification.scheduler;

import com.leetcodeted.notification.client.LeetCodeStatsClient;
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
    private final MongoTemplate mongoTemplate;

    @Scheduled(cron = "0 0 14 * * *")
    public void sendStreakReminder() {
        if (!notificationService.isEnabled()) {
            return;
        }
        List<BoardMember> members = boardMemberRepository.findAllByEmailIsNotNull();

        List<BoardMember> laggards = members.stream()
                .filter(m -> !leetCodeStatsClient.hasSolvedToday(m.getUsername()))
                .peek(m -> {
                    int streak = leetCodeStatsClient.fetchCurrentStreak(m.getUsername());
                    m.setCurrentStreak(streak);
                    mongoTemplate.updateFirst(
                            Query.query(Criteria.where("username").is(m.getUsername())),
                            new Update().set("currentStreak", streak),
                            BoardMember.class);
                })
                .toList();

        notificationService.publishStreakReminder(laggards);

        log.info("Streak reminder: {} members checked, {} laggards notified",
                members.size(), laggards.size());
    }
}
