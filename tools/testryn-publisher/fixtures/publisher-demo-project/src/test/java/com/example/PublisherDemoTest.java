package com.example;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Deliberately minimal, real JUnit 5 test class -- exists solely to produce genuine
 * Maven Surefire XML for testryn-publisher's publish-junit end-to-end verification
 * (a hand-written XML fixture alone is not accepted as proof). Four tests, matching
 * the four Testryn Test Cases created for this verification pass:
 * automationReference com.example.PublisherDemoTest#{passedTest,failedTest,
 * skippedTest,anotherPassedTest}.
 */
class PublisherDemoTest {

    @Test
    void passedTest() {
        assertEquals(4, 2 + 2);
    }

    @Test
    void failedTest() {
        fail("deliberate failure for publish-junit end-to-end verification");
    }

    @Test
    @Disabled("deliberately skipped for publish-junit end-to-end verification")
    void skippedTest() {
        fail("must never run");
    }

    @Test
    void anotherPassedTest() {
        assertEquals("ab", "a" + "b");
    }
}
