package com.testryn.common.error;

/**
 * Thrown when an explicit call to an external integration (e.g. a user-initiated
 * Jira issue lookup) fails because the upstream system is unreachable, misconfigured,
 * or rejected the request for a reason other than "not found". Translated to HTTP
 * 502 by {@link GlobalExceptionHandler}. Deliberately generic (not Jira-specific) so
 * future providers (GitHub, Azure DevOps) can reuse it.
 */
public class UpstreamServiceException extends RuntimeException {

    public UpstreamServiceException(String message) {
        super(message);
    }
}
