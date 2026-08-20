package com.testryn.common.error;

import java.util.List;

/**
 * A batch request (currently: bulk execution-result update, Abschnitt 4-7) failed
 * input/reference validation. Carries every violation found across the whole batch --
 * not just the first one hit -- so a CI/AI caller can fix everything in one
 * round-trip instead of resubmitting once per broken entry. Mapped to 400 by
 * {@link GlobalExceptionHandler}, reusing the existing {@link ApiError#fieldErrors()}
 * shape rather than introducing a parallel error model (Abschnitt 7).
 */
public class BulkValidationException extends RuntimeException {

    private final List<ApiError.FieldViolation> violations;

    public BulkValidationException(String message, List<ApiError.FieldViolation> violations) {
        super(message);
        this.violations = violations;
    }

    public List<ApiError.FieldViolation> getViolations() {
        return violations;
    }
}
