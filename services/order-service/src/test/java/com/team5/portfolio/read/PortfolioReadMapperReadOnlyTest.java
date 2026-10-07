package com.team5.portfolio.read;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

// The trading tables share one connection with the order path, so only discipline stops a write.
// This test is that discipline made executable: the module's mapper may contain SELECT statements only.
class PortfolioReadMapperReadOnlyTest {

    private static final Pattern WRITE_STATEMENT = Pattern.compile(
        "<\\s*(insert|update|delete)\\b|\\b(insert\\s+into|update\\s+\\w+\\s+set|delete\\s+from|truncate|merge\\s+into"
            + "|alter\\s+table|drop\\s+table|create\\s+table)\\b",
        Pattern.CASE_INSENSITIVE);

    @Test
    void theMapperContainsSelectStatementsOnly() throws Exception {
        String xml;
        try (InputStream in = getClass().getResourceAsStream("/mapper/PortfolioReadMapper.xml")) {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(xml.contains("<select"), "mapper should define select statements");
        assertFalse(WRITE_STATEMENT.matcher(xml).find(), "portfolio mapper must be read-only");
    }
}
