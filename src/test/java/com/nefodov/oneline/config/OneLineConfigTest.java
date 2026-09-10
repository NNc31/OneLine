package com.nefodov.oneline.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import java.time.Clock;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OneLineConfigTest {

    @Test
    @DisplayName("Application clock runs in UTC, so stored timestamps never depend on the host zone")
    void systemClockRunsInUtc() {
        Clock clock = new OneLineConfig().systemClock();
        assertEquals(ZoneOffset.UTC, clock.getZone());
    }

    @Test
    @DisplayName("Native hints cover the resources a native image cannot find by scanning bytecode")
    void nativeHintsCoverRuntimeResources() {
        RuntimeHints hints = new RuntimeHints();
        new OneLineConfig.NativeHints().registerHints(hints, getClass().getClassLoader());
        assertTrue(RuntimeHintsPredicates.resource().forResource("db/migration/V1_1__initial_schema.sql").test(hints));
        assertTrue(RuntimeHintsPredicates.resource().forResource("templates/chat.html").test(hints));
        assertTrue(RuntimeHintsPredicates.resource().forResource("static/js/app.js").test(hints));
    }
}
