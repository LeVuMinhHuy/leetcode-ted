package com.leetcodeted.notification.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetcodeted.notification.client.LeetCodeStatsClient.RecentSubmission;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class RecentSubmissionParsingTest {

    // Same builder RestClient's default Jackson converter uses.
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

    @Test
    void parsesStringTimestampAndIgnoresUnknownFields() throws Exception {
        String json = """
                {"title":"Two Sum","titleSlug":"two-sum","timestamp":"1792375779",
                 "statusDisplay":"Accepted","lang":"python3"}
                """;

        RecentSubmission submission = mapper.readValue(json, RecentSubmission.class);

        assertThat(submission.timestamp()).isEqualTo(1792375779L);
        assertThat(submission.accepted()).isTrue();
    }
}
