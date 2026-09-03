package io.airlift.mcp.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.Request;
import io.airlift.mcp.client.settings.StandardRequestCache;
import io.airlift.mcp.model.CacheScope;
import io.airlift.mcp.model.ListPromptsResult;
import io.airlift.mcp.model.ListRequest;
import io.airlift.mcp.model.ListResourceTemplatesResult;
import io.airlift.mcp.model.ListResourcesResult;
import io.airlift.mcp.model.ListToolsResult;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourcesUpdatedNotification;
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
import static io.airlift.mcp.model.Constants.METHOD_PROMPT_LIST;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_LIST;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_READ;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_TEMPLATES_LIST;
import static io.airlift.mcp.model.Constants.METHOD_TOOLS_LIST;
import static io.airlift.mcp.model.Constants.NOTIFICATION_MESSAGE;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROMPTS_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_UPDATED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_TOOLS_LIST_CHANGED;
import static io.airlift.mcp.model.ResultType.COMPLETE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestStandardRequestCache
{
    private static final URI URI_ONE = URI.create("http://localhost:1/mcp");
    private static final HeaderName IDENTITY_HEADER = HeaderName.of("X-Identity");
    private static final ListRequest LIST_REQUEST = new ListRequest(Optional.empty(), Optional.empty());

    @Test
    public void testResultIsServedFromCache()
    {
        StandardRequestCache cache = StandardRequestCache.builder(URI_ONE).build();
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
        StandardRequestCache cache = StandardRequestCache.builder(URI_ONE).build();
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
        StandardRequestCache cache = StandardRequestCache.builder(URI_ONE).build();
        AtomicInteger supplierCalls = new AtomicInteger();

        executeToolsList(cache, supplierCalls, toolsResult(OptionalInt.of(0), PUBLIC));
        executeToolsList(cache, supplierCalls, toolsResult(OptionalInt.of(0), PUBLIC));

        assertThat(supplierCalls).hasValue(2);
    }

    @Test
    public void testResultWithoutTtlUsesDefaultExpiration()
            throws InterruptedException
    {
        StandardRequestCache cache = StandardRequestCache.builder(URI_ONE)
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
        StandardRequestCache cache = StandardRequestCache.builder(URI_ONE).build();
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
        StandardRequestCache cache = StandardRequestCache.builder(URI_ONE).build();
        AtomicInteger supplierCalls = new AtomicInteger();

        ListToolsResult result = toolsResult(OptionalInt.of(60_000), PUBLIC);
        cache.executeRequest(request("Bearer one"), METHOD_TOOLS_LIST, LIST_REQUEST, ListToolsResult.class, Optional.empty(), countingSupplier(supplierCalls, result));
        cache.executeRequest(request("Bearer two"), METHOD_TOOLS_LIST, LIST_REQUEST, ListToolsResult.class, Optional.empty(), countingSupplier(supplierCalls, result));

        assertThat(supplierCalls).hasValue(1);
    }

    @Test
    public void testPrivateResultIsKeyedByCredential()
    {
        StandardRequestCache cache = StandardRequestCache.builder(URI_ONE).build();
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
        StandardRequestCache cache = StandardRequestCache.builder(URI_ONE).build();
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
        StandardRequestCache cache = StandardRequestCache.builder(URI_ONE).build();
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

    @Test
    public void testPrivateResultIsNotCachedWhenTheCallerCannotBeIdentified()
    {
        // the default auth mapper only reads the Authorization header, so a deployment that authenticates any
        // other way yields no credential. A caller scoped result must not be cached at all in that case - caching
        // it under the no-credential key would hand it to every other caller
        StandardRequestCache cache = StandardRequestCache.builder(URI_ONE).build();
        AtomicInteger supplierCalls = new AtomicInteger();

        ListToolsResult result = toolsResult(OptionalInt.of(60_000), PRIVATE);
        executeToolsList(cache, supplierCalls, result);
        executeToolsList(cache, supplierCalls, result);

        assertThat(supplierCalls).hasValue(2);
    }

    @Test
    public void testPrivateResultIsNotCachedForABlankCredential()
    {
        // a mapper that cannot identify this particular caller is treated the same as no mapper at all
        StandardRequestCache cache = StandardRequestCache.builder(URI_ONE)
                .withAuthMapper(_ -> "   ")
                .build();
        AtomicInteger supplierCalls = new AtomicInteger();

        ListToolsResult result = toolsResult(OptionalInt.of(60_000), PRIVATE);
        executeToolsList(cache, supplierCalls, result);
        executeToolsList(cache, supplierCalls, result);

        assertThat(supplierCalls).hasValue(2);
    }

    @Test
    public void testUnidentifiedCallerDoesNotReadAnotherCallersResult()
    {
        StandardRequestCache cache = StandardRequestCache.builder(URI_ONE).build();
        AtomicInteger supplierCalls = new AtomicInteger();

        ListToolsResult result = toolsResult(OptionalInt.of(60_000), PRIVATE);
        cache.executeRequest(request("Bearer one"), METHOD_TOOLS_LIST, LIST_REQUEST, ListToolsResult.class, Optional.empty(), countingSupplier(supplierCalls, result));
        assertThat(supplierCalls).hasValue(1);

        // a request with no credential must not be served the entry stored for "Bearer one"
        executeToolsList(cache, supplierCalls, result);
        assertThat(supplierCalls).hasValue(2);
    }

    @Test
    public void testPublicResultIsStillCachedWhenTheCallerCannotBeIdentified()
    {
        // failing closed applies only to caller scoped results - a shared result is still shared, so an
        // unauthenticated server keeps its caching
        StandardRequestCache cache = StandardRequestCache.builder(URI_ONE).build();
        AtomicInteger supplierCalls = new AtomicInteger();

        ListToolsResult result = toolsResult(OptionalInt.of(60_000), PUBLIC);
        executeToolsList(cache, supplierCalls, result);
        executeToolsList(cache, supplierCalls, result);

        assertThat(supplierCalls).hasValue(1);
    }

    @Test
    public void testPrivateResultIsCachedWithACustomAuthMapper()
    {
        // supplying a mapper for the deployment's own scheme restores caller scoped caching
        StandardRequestCache cache = StandardRequestCache.builder(URI_ONE)
                .withAuthMapper(request -> request.getHeader(IDENTITY_HEADER))
                .build();
        AtomicInteger supplierCalls = new AtomicInteger();

        ListToolsResult result = toolsResult(OptionalInt.of(60_000), PRIVATE);
        cache.executeRequest(identityRequest("one"), METHOD_TOOLS_LIST, LIST_REQUEST, ListToolsResult.class, Optional.empty(), countingSupplier(supplierCalls, result));
        cache.executeRequest(identityRequest("one"), METHOD_TOOLS_LIST, LIST_REQUEST, ListToolsResult.class, Optional.empty(), countingSupplier(supplierCalls, result));
        assertThat(supplierCalls).hasValue(1);

        cache.executeRequest(identityRequest("two"), METHOD_TOOLS_LIST, LIST_REQUEST, ListToolsResult.class, Optional.empty(), countingSupplier(supplierCalls, result));
        assertThat(supplierCalls).hasValue(2);
    }

    @Test
    public void testCacheBoundToOneEndpointRejectsAnother()
    {
        // a cache belongs to the endpoint it was built for - that URI is what RequestCacheFactory is handed. Two
        // MCP endpoints can differ only by path, so sharing one cache between them would cross their results
        StandardRequestCache cache = StandardRequestCache.builder(URI.create("http://localhost:1/mcp/alpha")).build();
        AtomicInteger supplierCalls = new AtomicInteger();
        ListToolsResult result = toolsResult(OptionalInt.of(60_000), PUBLIC);

        Request alpha = preparePost().setUri(URI.create("http://localhost:1/mcp/alpha")).build();
        cache.executeRequest(alpha, METHOD_TOOLS_LIST, LIST_REQUEST, ListToolsResult.class, Optional.empty(), countingSupplier(supplierCalls, result));
        assertThat(supplierCalls).hasValue(1);

        Request beta = preparePost().setUri(URI.create("http://localhost:1/mcp/beta")).build();
        assertThatThrownBy(() -> cache.executeRequest(beta, METHOD_TOOLS_LIST, LIST_REQUEST, ListToolsResult.class, Optional.empty(), countingSupplier(supplierCalls, result)))
                .isInstanceOf(IllegalArgumentException.class);

        // it is a wiring error rather than a cache miss, so the rejected request never reached the supplier
        assertThat(supplierCalls).hasValue(1);
    }

    @Test
    public void testToolsListChangedPurgesToolsListOnly()
    {
        StandardRequestCache cache = StandardRequestCache.builder(URI_ONE).build();
        AtomicInteger toolCalls = new AtomicInteger();
        AtomicInteger promptCalls = new AtomicInteger();

        executeToolsList(cache, toolCalls, toolsResult(OptionalInt.of(60_000), PUBLIC));
        executePromptsList(cache, promptCalls, promptsResult());
        assertThat(toolCalls).hasValue(1);
        assertThat(promptCalls).hasValue(1);

        cache.accept(null, NOTIFICATION_TOOLS_LIST_CHANGED, Optional.empty());

        // the tools list is refetched, and nothing else was disturbed
        executeToolsList(cache, toolCalls, toolsResult(OptionalInt.of(60_000), PUBLIC));
        executePromptsList(cache, promptCalls, promptsResult());
        assertThat(toolCalls).hasValue(2);
        assertThat(promptCalls).hasValue(1);
    }

    @Test
    public void testPromptsListChangedPurgesPromptsList()
    {
        StandardRequestCache cache = StandardRequestCache.builder(URI_ONE).build();
        AtomicInteger supplierCalls = new AtomicInteger();

        executePromptsList(cache, supplierCalls, promptsResult());
        cache.accept(null, NOTIFICATION_PROMPTS_LIST_CHANGED, Optional.empty());
        executePromptsList(cache, supplierCalls, promptsResult());

        assertThat(supplierCalls).hasValue(2);
    }

    @Test
    public void testResourcesListChangedPurgesResourcesAndTemplates()
    {
        StandardRequestCache cache = StandardRequestCache.builder(URI_ONE).build();
        AtomicInteger resourceCalls = new AtomicInteger();
        AtomicInteger templateCalls = new AtomicInteger();

        executeResourcesList(cache, resourceCalls);
        executeResourceTemplatesList(cache, templateCalls);

        // a change to the resource list affects the templates list too
        cache.accept(null, NOTIFICATION_RESOURCES_LIST_CHANGED, Optional.empty());

        executeResourcesList(cache, resourceCalls);
        executeResourceTemplatesList(cache, templateCalls);
        assertThat(resourceCalls).hasValue(2);
        assertThat(templateCalls).hasValue(2);
    }

    @Test
    public void testListChangedPurgesEveryCursor()
    {
        StandardRequestCache cache = StandardRequestCache.builder(URI_ONE).build();
        AtomicInteger supplierCalls = new AtomicInteger();
        ListToolsResult result = toolsResult(OptionalInt.of(60_000), PUBLIC);

        cache.executeRequest(request(), METHOD_TOOLS_LIST, LIST_REQUEST, ListToolsResult.class, Optional.of("page-2"), countingSupplier(supplierCalls, result));
        executeToolsList(cache, supplierCalls, result);
        assertThat(supplierCalls).hasValue(2);

        cache.accept(null, NOTIFICATION_TOOLS_LIST_CHANGED, Optional.empty());

        // a paginated listing is cached per cursor, so every page has to go
        cache.executeRequest(request(), METHOD_TOOLS_LIST, LIST_REQUEST, ListToolsResult.class, Optional.of("page-2"), countingSupplier(supplierCalls, result));
        executeToolsList(cache, supplierCalls, result);
        assertThat(supplierCalls).hasValue(4);
    }

    @Test
    public void testResourceUpdatedPurgesThatResourceRead()
    {
        StandardRequestCache cache = StandardRequestCache.builder(URI_ONE).build();
        AtomicInteger oneCalls = new AtomicInteger();
        AtomicInteger twoCalls = new AtomicInteger();

        executeResourceRead(cache, oneCalls, "file://one.txt");
        executeResourceRead(cache, twoCalls, "file://two.txt");
        assertThat(oneCalls).hasValue(1);
        assertThat(twoCalls).hasValue(1);

        cache.accept(null, NOTIFICATION_RESOURCES_UPDATED, Optional.of(new ResourcesUpdatedNotification("file://one.txt")));

        // only the resource the server named is stale
        executeResourceRead(cache, oneCalls, "file://one.txt");
        executeResourceRead(cache, twoCalls, "file://two.txt");
        assertThat(oneCalls).hasValue(2);
        assertThat(twoCalls).hasValue(1);
    }

    @Test
    public void testUnrelatedNotificationLeavesTheCacheIntact()
    {
        StandardRequestCache cache = StandardRequestCache.builder(URI_ONE).build();
        AtomicInteger supplierCalls = new AtomicInteger();

        executeToolsList(cache, supplierCalls, toolsResult(OptionalInt.of(60_000), PUBLIC));
        cache.accept(null, NOTIFICATION_MESSAGE, Optional.empty());
        executeToolsList(cache, supplierCalls, toolsResult(OptionalInt.of(60_000), PUBLIC));

        assertThat(supplierCalls).hasValue(1);
    }

    private static void executePromptsList(StandardRequestCache cache, AtomicInteger supplierCalls, ListPromptsResult result)
    {
        cache.executeRequest(request(), METHOD_PROMPT_LIST, LIST_REQUEST, ListPromptsResult.class, Optional.empty(), countingSupplier(supplierCalls, result));
    }

    private static void executeResourcesList(StandardRequestCache cache, AtomicInteger supplierCalls)
    {
        ListResourcesResult result = new ListResourcesResult(Optional.of(COMPLETE), ImmutableList.of(), Optional.empty(), OptionalInt.of(60_000), Optional.of(PUBLIC), Optional.empty());
        cache.executeRequest(request(), METHOD_RESOURCES_LIST, LIST_REQUEST, ListResourcesResult.class, Optional.empty(), countingSupplier(supplierCalls, result));
    }

    private static void executeResourceTemplatesList(StandardRequestCache cache, AtomicInteger supplierCalls)
    {
        ListResourceTemplatesResult result = new ListResourceTemplatesResult(Optional.of(COMPLETE), ImmutableList.of(), Optional.empty(), OptionalInt.of(60_000), Optional.of(PUBLIC), Optional.empty());
        cache.executeRequest(request(), METHOD_RESOURCES_TEMPLATES_LIST, LIST_REQUEST, ListResourceTemplatesResult.class, Optional.empty(), countingSupplier(supplierCalls, result));
    }

    private static void executeResourceRead(StandardRequestCache cache, AtomicInteger supplierCalls, String resourceUri)
    {
        ReadResourceResult result = new ReadResourceResult(
                Optional.of(COMPLETE),
                Optional.of(ImmutableList.of(new ResourceContents("name", resourceUri, "text/plain", "hello"))),
                OptionalInt.of(60_000),
                Optional.of(PUBLIC),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
        cache.executeRequest(request(), METHOD_RESOURCES_READ, new ReadResourceRequest(resourceUri), ReadResourceResult.class, Optional.empty(), countingSupplier(supplierCalls, result));
    }

    private static ListPromptsResult promptsResult()
    {
        return new ListPromptsResult(Optional.of(COMPLETE), ImmutableList.of(), Optional.empty(), OptionalInt.of(60_000), Optional.of(PUBLIC), Optional.empty());
    }

    @Test
    public void testUnreadableResourceUpdatedNotificationIsIgnored()
    {
        StandardRequestCache cache = StandardRequestCache.builder(URI_ONE).build();
        AtomicInteger supplierCalls = new AtomicInteger();

        executeResourceRead(cache, supplierCalls, "file://one.txt");

        // accept() runs on the thread reading the notification stream, and the cache is consulted before the
        // connection's own consumer - throwing here would end the subscription and hide every later notification
        cache.accept(null, NOTIFICATION_RESOURCES_UPDATED, Optional.empty());
        cache.accept(null, NOTIFICATION_RESOURCES_UPDATED, Optional.of(ImmutableList.of("not a notification")));

        // and the entry is left alone rather than guessed at
        executeResourceRead(cache, supplierCalls, "file://one.txt");
        assertThat(supplierCalls).hasValue(1);
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

    private static Request identityRequest(String identity)
    {
        return preparePost().setUri(URI_ONE).setHeader(IDENTITY_HEADER, identity).build();
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
