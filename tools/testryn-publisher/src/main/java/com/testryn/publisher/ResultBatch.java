package com.testryn.publisher;

import java.util.List;

/**
 * A parsed, input-format-agnostic batch of results to publish (Abschnitt 17: the
 * publisher core depends only on this, never on the concrete input format).
 * {@code executionId} is optional here -- the input file may or may not carry one
 * (Abschnitt 14); {@link PublisherCli} resolves the final execution id from
 * {@code --execution-id} and/or this field, rejecting the two if they disagree
 * (same "no silent priority" principle as resultId/automationReference in ADR 0010).
 */
public record ResultBatch(String executionId, List<PublisherResultInput> results) {
}
