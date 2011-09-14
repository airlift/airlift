package com.proofpoint.event.client;

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Ordering;
import com.proofpoint.event.EventSubmissionFailedException;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.Collections;
import java.util.Map;

import static com.google.common.collect.ImmutableMap.of;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

public class TestEventSubmissionFailedException
{
    @Test(expectedExceptions = NullPointerException.class, expectedExceptionsMessageRegExp = "causes is null")
    public void testRejectsNull()
    {
        //noinspection ThrowableInstanceNeverThrown
        new EventSubmissionFailedException("foo", null);
    }

    @Test
    public void testEmptyCause()
    {
        EventSubmissionFailedException e = new EventSubmissionFailedException("foo", Collections.<URI, Exception>emptyMap());

        assertNull(e.getCause());
    }

    @Test
    public void testSingleCause()
    {
        RuntimeException cause = new RuntimeException();
        EventSubmissionFailedException e = new EventSubmissionFailedException("foo", of(URI.create("/"), cause));

        assertSame(e.getCause(), cause);
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

        EventSubmissionFailedException e = new EventSubmissionFailedException("foo", causes);

        assertSame(e.getCause(), cause1);
    }
}
