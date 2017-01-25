/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.event.client;

import com.google.common.collect.ImmutableMap;

import java.net.URI;
import java.util.Map;

import static com.google.common.base.Predicates.notNull;
import static com.google.common.collect.Iterables.find;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class EventSubmissionFailedException
    extends RuntimeException
{
    private final Map<URI, Throwable> causes;

    public EventSubmissionFailedException(String type, String pool, Map<URI, ? extends Throwable> causes)
    {
        super(format("Failed to submit events to service=[%s], pool [%s]", type, pool));

        requireNonNull(causes, "causes is null");

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
