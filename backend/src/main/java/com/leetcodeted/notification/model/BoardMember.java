package com.leetcodeted.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Maps the existing {@code board_members} collection, which is also written by the Next.js app.
 */
@Document(collection = "board_members")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardMember {

    @Id
    private String id;

    private String username;

    private String displayName;

    /** Optional; newly added field, backfilled by a separate migration. */
    private String email;

    private Instant joinedAt;

    // Solve-poller state. Written only via partial $set updates — never save() this entity,
    // or fields the Next.js app owns could be overwritten.
    private Long lastSeenSubmissionTs;

    private String notifiedDate;

    private List<String> notifiedSlugs;

    /** Current daily solve streak, synced from LeetCode when the streak reminder runs. */
    private Integer currentStreak;
}
