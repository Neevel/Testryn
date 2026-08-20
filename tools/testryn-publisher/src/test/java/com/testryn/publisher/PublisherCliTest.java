package com.testryn.publisher;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublisherCliTest {

    @Test
    void parsesAllFlags() {
        PublisherOptions options = PublisherCli.parse(
                new String[] {"publish", "--base-url", "http://localhost:8080",
                        "--execution-id", "exec-1", "--results", "results.json"},
                "secret-token");

        assertThat(options.baseUrl()).isEqualTo("http://localhost:8080");
        assertThat(options.executionId()).isEqualTo("exec-1");
        assertThat(options.resultsPath()).isEqualTo("results.json");
        assertThat(options.apiToken()).isEqualTo("secret-token");
    }

    @Test
    void executionIdAndResultsAreOptionalOnTheCli() {
        PublisherOptions options = PublisherCli.parse(
                new String[] {"publish", "--base-url", "http://localhost:8080"}, null);

        assertThat(options.executionId()).isNull();
        assertThat(options.resultsPath()).isNull();
    }

    @Test
    void requiresBaseUrl() {
        assertThatThrownBy(() -> PublisherCli.parse(new String[] {"publish"}, null))
                .isInstanceOf(PublisherCli.UsageException.class)
                .hasMessageContaining("--base-url");
    }

    @Test
    void rejectsAnUnknownCommand() {
        assertThatThrownBy(() -> PublisherCli.parse(new String[] {"delete-everything"}, null))
                .isInstanceOf(PublisherCli.UsageException.class);
    }

    @Test
    void rejectsAnUnknownOption() {
        assertThatThrownBy(() -> PublisherCli.parse(
                new String[] {"publish", "--base-url", "http://localhost:8080", "--totally-fake-flag"}, null))
                .isInstanceOf(PublisherCli.UsageException.class);
    }

    @Test
    void rejectsAFlagMissingItsValue() {
        assertThatThrownBy(() -> PublisherCli.parse(new String[] {"publish", "--base-url"}, null))
                .isInstanceOf(PublisherCli.UsageException.class);
    }

    @Test
    void emptyArgsShowsUsage() {
        assertThatThrownBy(() -> PublisherCli.parse(new String[0], null))
                .isInstanceOf(PublisherCli.UsageException.class)
                .hasMessageContaining("Usage:");
    }

    @Test
    void neverReadsATokenFromCommandLineArguments() {
        // There is deliberately no --api-token / --token flag to parse.
        PublisherOptions options = PublisherCli.parse(
                new String[] {"publish", "--base-url", "http://localhost:8080"}, "from-env-only");

        assertThat(options.apiToken()).isEqualTo("from-env-only");
    }
}
