package io.airlift.mcp.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.http.client.Request;
import io.airlift.mcp.client.settings.StandardRequestCache;
import io.airlift.mcp.model.CacheScope;
import io.airlift.mcp.model.ListRequest;
import io.airlift.mcp.model.ListToolsResult;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.ResourceContents;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static io.airlift.http.client.HeaderNames.AUTHORIZATION;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.mcp.model.CacheScope.PRIVATE;
import static io.airlift.mcp.model.CacheScope.PUBLIC;
import static io.airlift.mcp.model.Constants.METHOD_ELICITATION_CREATE;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_READ;
import static io.airlift.mcp.model.Constants.METHOD_TOOLS_LIST;
import static io.airlift.mcp.model.ResultType.COMPLETE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class TestStandardRequestCache
{
    private static final URI URI_ONE = URI.create("http://localhost:1/mcp");
    private static final ListRequest LIST_REQUEST = new ListRequest(Optional.empty(), Optional.empty());

    @Test
    public void testResultIsServedFromCache()
    {
        StandardRequestCache cache = StandardRequestCache.builder().build();
        AtomicInteger supplierCalls = new AtomicInteger();

        ListToolsResult result = toolsResult(OptionalInt.of(60_000), PUBLIC);
        for (int i = 0; i < 3; i++) {
            ListToolsResult served = cache.executeRequest(request(), METHOD_TOOLS_LIST, LIST_REQUEST, ListToolsResult.class, Optional.empty(), () -> {
                supplierCalls.incrementAndGet();
                return result;
            });
            assertThat(served).isSameAs(result);
        }

        assertThat(supplierCalls).hasValue(1);
    }

    @Test
    public void testTtlExpires()
            throws InterruptedException
    {
        StandardRequestCache cache = StandardRequestCache.builder().build();
        AtomicInteger supplierCalls = new AtomicInteger();

        // a 1ms ttl must expire the entry - previously the age calculation was inverted and
        // entries never expired
        executeToolsList(cache, supplierCalls, toolsResult(OptionalInt.of(1), PUBLIC));
        MILLISECONDS.sleep(50);
        executeToolsList(cache, supplierCalls, toolsResult(OptionalInt.of(1), PUBLIC));

        assertThat(supplierCalls).hasValue(2);
    }

    @Test
    public void testZeroTtlIsNotCached()
    {
        StandardRequestCache cache = StandardRequestCache.builder().build();
        AtomicInteger supplierCalls = new AtomicInteger();

        executeToolsList(cache, supplierCalls, toolsResult(OptionalInt.of(0), PUBLIC));
        executeToolsList(cache, supplierCalls, toolsResult(OptionalInt.of(0), PUBLIC));

        assertThat(supplierCalls).hasValue(2);
    }

    @Test
    public void testResultWithoutTtlUsesDefaultExpiration()
            throws InterruptedException
    {
        StandardRequestCache cache = StandardRequestCache.builder()
                .withDefaultTtl(Duration.ofMillis(50))
                .build();
        AtomicInteger supplierCalls = new AtomicInteger();

        executeToolsList(cache, supplierCalls, toolsResult(OptionalInt.empty(), PUBLIC));
        executeToolsList(cache, supplierCalls, toolsResult(OptionalInt.empty(), PUBLIC));
        assertThat(supplierCalls).hasValue(1);

        MILLISECONDS.sleep(100);
        executeToolsList(cache, supplierCalls, toolsResult(OptionalInt.empty(), PUBLIC));
        assertThat(supplierCalls).hasValue(2);
    }

    @Test
    public void testInputRequiredResultIsNotCached()
    {
        StandardRequestCache cache = StandardRequestCache.builder().build();
        AtomicInteger supplierCalls = new AtomicInteger();

        ReadResourceRequest readRequest = new ReadResourceRequest("file://one.txt");
        ReadResourceResult inputRequired = ReadResourceResult.inputRequestsBuilder()
                .add("confirm", METHOD_ELICITATION_CREATE, ImmutableMap.of("message", "Are you sure?"))
                .build();
        ReadResourceResult complete = new ReadResourceResult(
                Optional.of(COMPLETE),
                Optional.of(ImmutableList.of(new ResourceContents("one", "file://one.txt", "text/plain", "hello"))),
                OptionalInt.of(60_000),
                Optional.of(PUBLIC),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());

        // the input-required result must not be cached - a cached copy would answer the retry
        // with the very input requests the retry is answering
        ReadResourceResult first = cache.executeRequest(request(), METHOD_RESOURCES_READ, readRequest, ReadResourceResult.class, Optional.empty(), () -> {
            supplierCalls.incrementAndGet();
            return inputRequired;
        });
        assertThat(first).isSameAs(inputRequired);

        ReadResourceResult second = cache.executeRequest(request(), METHOD_RESOURCES_READ, readRequest, ReadResourceResult.class, Optional.empty(), () -> {
            supplierCalls.incrementAndGet();
            return complete;
        });
        assertThat(second).isSameAs(complete);
        assertThat(supplierCalls).hasValue(2);
    }

    @Test
    public void testPublicResultIsSharedAcrossCredentials()
    {
        StandardRequestCache cache = StandardRequestCache.builder().build();
        AtomicInteger supplierCalls = new AtomicInteger();

        ListToolsResult result = toolsResult(OptionalInt.of(60_000), PUBLIC);
        cache.executeRequest(request("Bearer one"), METHOD_TOOLS_LIST, LIST_REQUEST, ListToolsResult.class, Optional.empty(), countingSupplier(supplierCalls, result));
        cache.executeRequest(request("Bearer two"), METHOD_TOOLS_LIST, LIST_REQUEST, ListToolsResult.class, Optional.empty(), countingSupplier(supplierCalls, result));

        assertThat(supplierCalls).hasValue(1);
    }

    @Test
    public void testPrivateResultIsKeyedByCredential()
    {
        StandardRequestCache cache = StandardRequestCache.builder().build();
        AtomicInteger supplierCalls = new AtomicInteger();

        ListToolsResult result = toolsResult(OptionalInt.of(60_000), PRIVATE);
        cache.executeRequest(request("Bearer one"), METHOD_TOOLS_LIST, LIST_REQUEST, ListToolsResult.class, Optional.empty(), countingSupplier(supplierCalls, result));
        cache.executeRequest(request("Bearer one"), METHOD_TOOLS_LIST, LIST_REQUEST, ListToolsResult.class, Optional.empty(), countingSupplier(supplierCalls, result));
        assertThat(supplierCalls).hasValue(1);

        cache.executeRequest(request("Bearer two"), METHOD_TOOLS_LIST, LIST_REQUEST, ListToolsResult.class, Optional.empty(), countingSupplier(supplierCalls, result));
        assertThat(supplierCalls).hasValue(2);
    }

    @Test
    public void testCursorIsPartOfTheKey()
    {
        StandardRequestCache cache = StandardRequestCache.builder().build();
        AtomicInteger supplierCalls = new AtomicInteger();

        // each page must be cached separately - previously the cursor was ignored and page
        // requests were served the first page's cached result
        ListToolsResult pageOne = toolsResult(OptionalInt.of(60_000), PUBLIC);
        ListToolsResult pageTwo = toolsResult(OptionalInt.of(60_000), PUBLIC);

        assertThat(cache.executeRequest(request(), METHOD_TOOLS_LIST, LIST_REQUEST, ListToolsResult.class, Optional.empty(), countingSupplier(supplierCalls, pageOne))).isSameAs(pageOne);
        assertThat(cache.executeRequest(request(), METHOD_TOOLS_LIST, LIST_REQUEST, ListToolsResult.class, Optional.of("page-2"), countingSupplier(supplierCalls, pageTwo))).isSameAs(pageTwo);
        assertThat(supplierCalls).hasValue(2);

        // and each page is served from its own entry
        assertThat(cache.executeRequest(request(), METHOD_TOOLS_LIST, LIST_REQUEST, ListToolsResult.class, Optional.empty(), countingSupplier(supplierCalls, pageOne))).isSameAs(pageOne);
        assertThat(cache.executeRequest(request(), METHOD_TOOLS_LIST, LIST_REQUEST, ListToolsResult.class, Optional.of("page-2"), countingSupplier(supplierCalls, pageTwo))).isSameAs(pageTwo);
        assertThat(supplierCalls).hasValue(2);
    }

    @Test
    public void testResourceReadsAreKeyedByUri()
    {
        StandardRequestCache cache = StandardRequestCache.builder().build();
        AtomicInteger supplierCalls = new AtomicInteger();

        ReadResourceResult result = new ReadResourceResult(
                Optional.of(COMPLETE),
                Optional.of(ImmutableList.of(new ResourceContents("one", "file://one.txt", "text/plain", "hello"))),
                OptionalInt.of(60_000),
                Optional.of(PUBLIC),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());

        cache.executeRequest(request(), METHOD_RESOURCES_READ, new ReadResourceRequest("file://one.txt"), ReadResourceResult.class, Optional.empty(), countingSupplier(supplierCalls, result));
        cache.executeRequest(request(), METHOD_RESOURCES_READ, new ReadResourceRequest("file://one.txt"), ReadResourceResult.class, Optional.empty(), countingSupplier(supplierCalls, result));
        cache.executeRequest(request(), METHOD_RESOURCES_READ, new ReadResourceRequest("file://two.txt"), ReadResourceResult.class, Optional.empty(), countingSupplier(supplierCalls, result));

        assertThat(supplierCalls).hasValue(2);
    }

    private static void executeToolsList(StandardRequestCache cache, AtomicInteger supplierCalls, ListToolsResult result)
    {
        cache.executeRequest(request(), METHOD_TOOLS_LIST, LIST_REQUEST, ListToolsResult.class, Optional.empty(), countingSupplier(supplierCalls, result));
    }

    private static <R> Supplier<R> countingSupplier(AtomicInteger supplierCalls, R result)
    {
        return () -> {
            supplierCalls.incrementAndGet();
            return result;
        };
    }

    private static Request request()
    {
        return preparePost().setUri(URI_ONE).build();
    }

    private static Request request(String authorization)
    {
        return preparePost().setUri(URI_ONE).setHeader(AUTHORIZATION, authorization).build();
    }

    private static ListToolsResult toolsResult(OptionalInt ttlMs, CacheScope cacheScope)
    {
        return new ListToolsResult(Optional.of(COMPLETE), ImmutableList.of(), Optional.empty(), ttlMs, Optional.of(cacheScope), Optional.empty());
    }
}
