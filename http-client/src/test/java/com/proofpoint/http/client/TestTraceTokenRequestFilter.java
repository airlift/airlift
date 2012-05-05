package com.proofpoint.http.client;

import com.google.common.collect.ImmutableList;
import com.proofpoint.tracetoken.TraceTokenManager;
import org.testng.annotations.Test;

import java.net.URI;

import static com.proofpoint.http.client.TraceTokenRequestFilter.TRACETOKEN_HEADER;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;

public class TestTraceTokenRequestFilter
{
    @Test
    public void testBasic()
    {
        TraceTokenManager manager = new TraceTokenManager();
        manager.registerRequestToken("testBasic");
        TraceTokenRequestFilter filter = new TraceTokenRequestFilter(manager);
        Request original = RequestBuilder.prepareGet().setUri(URI.create("http://example.com")).build();

        Request filtered = filter.filterRequest(original);

        assertNotSame(filter, original);
        assertEquals(filtered.getUri(), original.getUri());
        assertEquals(original.getHeaders().size(), 0);
        assertEquals(filtered.getHeaders().size(), 1);
        assertEquals(filtered.getHeaders().get(TRACETOKEN_HEADER), ImmutableList.of("testBasic"));
    }

    @Test
    public void testSameRequestReturnedWhenTraceTokenNotSet()
    {
        TraceTokenManager manager = new TraceTokenManager();
        TraceTokenRequestFilter filter = new TraceTokenRequestFilter(manager);
        Request original =  RequestBuilder.prepareGet().setUri(URI.create("http://example.com")).build();

        Request request = filter.filterRequest(original);

        assertSame(request, original);
    }
}
