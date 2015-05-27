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

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Ordering;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.Collections;
import java.util.Map;

import static com.google.common.collect.ImmutableMap.of;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class TestEventSubmissionFailedException
{
    @Test(expectedExceptions = NullPointerException.class, expectedExceptionsMessageRegExp = "causes is null")
    public void testRejectsNull()
    {
        throw new EventSubmissionFailedException("service", "type", null);
    }

    @Test
    public void testEmptyCause()
    {
        EventSubmissionFailedException e = new EventSubmissionFailedException("service", "type", Collections.<URI, Exception>emptyMap());

        assertNull(e.getCause());
    }

    @Test
    public void testSingleCause()
    {
        RuntimeException cause = new RuntimeException();
        EventSubmissionFailedException e = new EventSubmissionFailedException("service", "type", of(URI.create("/"), cause));

        assertSame(e.getCause(), cause);
    }

    @Test
    public void testMessageMentionsServiceAndType()
    {
        EventSubmissionFailedException e = new EventSubmissionFailedException("serviceX", "typeY", Collections.<URI, Throwable>emptyMap());

        assertTrue(e.getMessage().contains("serviceX"));
        assertTrue(e.getMessage().contains("typeY"));
    }

    @Test
    public void testPicksFirstCause()
    {
        URI uri1 = URI.create("/y");
        URI uri2 = URI.create("/x");
        RuntimeException cause1 = new RuntimeException("y");
        RuntimeException cause2 = new RuntimeException("x");

        Map<URI, RuntimeException> causes = ImmutableSortedMap.<URI, RuntimeException>orderedBy(Ordering.explicit(uri1, uri2))
            .put(uri1, cause1)
            .put(uri2, cause2)
            .build();

        EventSubmissionFailedException e = new EventSubmissionFailedException("service", "type", causes);

        assertSame(e.getCause(), cause1);
    }
}
