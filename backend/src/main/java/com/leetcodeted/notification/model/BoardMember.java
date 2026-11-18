package com.leetcodeted.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

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
}
