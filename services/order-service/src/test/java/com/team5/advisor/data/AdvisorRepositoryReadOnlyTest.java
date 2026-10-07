package com.team5.advisor.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

// The advisor shares the Trade API's database connection, so only discipline stops it writing to the trading
// tables. This test is that discipline made executable: the repository reads only, and every call runs in a
// read-only transaction, which Postgres itself enforces.
class AdvisorRepositoryReadOnlyTest {

    private static final Pattern WRITE_STATEMENT = Pattern.compile(
        "\\b(insert\\s+into|update\\s+\\w+\\s+set|delete\\s+from|truncate|merge\\s+into|alter\\s+table|drop\\s+table"
            + "|create\\s+table)\\b",
        Pattern.CASE_INSENSITIVE);

    @Test
    void everyCallRunsInAReadOnlyTransaction() {
        Transactional transactional = AdvisorRepository.class.getAnnotation(Transactional.class);

        assertThat(transactional).as("@Transactional on AdvisorRepository").isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    void theRepositoryContainsNoWriteStatement() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/team5/advisor/data/AdvisorRepository.java"));

        assertThat(source).contains("SELECT");
        assertThat(WRITE_STATEMENT.matcher(source).find()).as("AdvisorRepository must only read").isFalse();
    }
}
