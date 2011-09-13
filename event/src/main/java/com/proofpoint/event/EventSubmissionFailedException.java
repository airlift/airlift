package com.proofpoint.event;

import com.google.common.collect.ImmutableMap;

import java.net.URI;
import java.util.Map;

public class EventSubmissionFailedException
    extends RuntimeException
{
    private final Map<URI, Exception> causes;

    public EventSubmissionFailedException(String message, Map<URI, Exception> causes)
    {
        this.causes = ImmutableMap.copyOf(causes);
    }

    /**
     * Gets the underlying cause for each of the servers we attempted to submit to
     */
    public Map<URI, Exception> getCauses()
    {
        return causes;
    }
}
