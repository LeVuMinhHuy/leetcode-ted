package com.leetcodeted.notification.scheduler;

import com.leetcodeted.notification.client.LeetCodeStatsClient;
import com.leetcodeted.notification.client.LeetCodeStatsClient.RecentSubmission;
import com.leetcodeted.notification.model.BoardMember;
import com.leetcodeted.notification.repository.BoardMemberRepository;
import com.leetcodeted.notification.service.NotificationService;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SolvedSubmissionPollerTest {

    private final BoardMemberRepository repository = mock(BoardMemberRepository.class);
    private final LeetCodeStatsClient client = mock(LeetCodeStatsClient.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);

    private SolvedSubmissionPoller poller;
    private long now;
    private String today;

    @BeforeEach
    void setUp() {
        poller = new SolvedSubmissionPoller(repository, client, notificationService, mongoTemplate);
        now = Instant.now().getEpochSecond();
        today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();
    }

    @Test
    void pollDoesNothingWhenFlagOff() {
        when(notificationService.isEnabled()).thenReturn(false);

        poller.poll();

        verifyNoInteractions(repository, client, mongoTemplate);
        verify(notificationService, never()).publishMemberSolved(any(), any());
    }

    @Test
    void firstPollSeedsWatermarkWithoutNotifying() {
        BoardMember member = member(null, null, null);

        poller.process(member, List.of(accepted("two-sum", now - 60)));

        verify(notificationService, never()).publishMemberSolved(any(), any());
        assertThat(savedSet().getLong("lastSeenSubmissionTs")).isGreaterThanOrEqualTo(now);
    }

    @Test
    void publishesNewAcceptedOncePerSlugAndAdvancesWatermark() {
        long watermark = now - 600;
        BoardMember member = member(watermark, null, null);

        poller.process(member, List.of(
                accepted("two-sum", now - 10),
                accepted("two-sum", now - 20),
                wrongAnswer("add-two-numbers", now - 5),
                accepted("valid-parentheses", now - 30),
                accepted("old-one", watermark - 1)));

        ArgumentCaptor<RecentSubmission> captor = ArgumentCaptor.forClass(RecentSubmission.class);
        verify(notificationService, times(2)).publishMemberSolved(eq(member), captor.capture());
        assertThat(captor.getAllValues()).extracting(RecentSubmission::titleSlug)
                .containsExactly("valid-parentheses", "two-sum");

        Document set = savedSet();
        assertThat(set.getLong("lastSeenSubmissionTs")).isEqualTo(now - 5);
        assertThat(set.getString("notifiedDate")).isEqualTo(today);
        assertThat(set.getList("notifiedSlugs", String.class)).containsExactly("valid-parentheses", "two-sum");
    }

    @Test
    void skipsSlugAlreadyNotifiedToday() {
        BoardMember member = member(now - 600, today, List.of("two-sum"));

        poller.process(member, List.of(accepted("two-sum", now - 10)));

        verify(notificationService, never()).publishMemberSolved(any(), any());
    }

    @Test
    void notifiesAgainWhenPreviousNotificationWasOnAnotherDay() {
        BoardMember member = member(now - 600, "2000-01-01", List.of("two-sum"));

        poller.process(member, List.of(accepted("two-sum", now - 10)));

        verify(notificationService).publishMemberSolved(eq(member), any());
    }

    @Test
    void skipsStaleBacklogButStillAdvancesWatermark() {
        BoardMember member = member(now - 7200, null, null);

        poller.process(member, List.of(accepted("two-sum", now - 3600)));

        verify(notificationService, never()).publishMemberSolved(any(), any());
        assertThat(savedSet().getLong("lastSeenSubmissionTs")).isEqualTo(now - 3600);
    }

    @Test
    void noWriteWhenNothingNew() {
        BoardMember member = member(now, null, null);

        poller.process(member, List.of(accepted("two-sum", now - 10)));

        verifyNoInteractions(mongoTemplate);
    }

    private Document savedSet() {
        ArgumentCaptor<Update> captor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(any(Query.class), captor.capture(), eq(BoardMember.class));
        return (Document) captor.getValue().getUpdateObject().get("$set");
    }

    private static BoardMember member(Long watermark, String notifiedDate, List<String> slugs) {
        return BoardMember.builder()
                .username("tedvu")
                .lastSeenSubmissionTs(watermark)
                .notifiedDate(notifiedDate)
                .notifiedSlugs(slugs)
                .build();
    }

    private static RecentSubmission accepted(String slug, long ts) {
        return new RecentSubmission(slug, slug, ts, "Accepted");
    }

    private static RecentSubmission wrongAnswer(String slug, long ts) {
        return new RecentSubmission(slug, slug, ts, "Wrong Answer");
    }
}
