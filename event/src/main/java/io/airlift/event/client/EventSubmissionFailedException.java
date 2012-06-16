package io.airlift.event.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import java.net.URI;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.notNull;
import static com.google.common.collect.Iterables.find;
import static java.lang.String.format;

public class EventSubmissionFailedException
    extends RuntimeException
{
    private final Map<URI, Throwable> causes;

    public EventSubmissionFailedException(String type, String pool, Map<URI, ? extends Throwable> causes)
    {
        super(format("Failed to submit events to service=[%s], pool [%s]", type, pool));

        Preconditions.checkNotNull(causes, "causes is null");

        Throwable cause = find(causes.values(), notNull(), null);
        initCause(cause);

        this.causes = ImmutableMap.copyOf(causes);
    }

    /**
     * Gets the underlying cause for each of the servers we attempted to submit to
     */
    public Map<URI, Throwable> getCauses()
    {
        return causes;
    }
}
