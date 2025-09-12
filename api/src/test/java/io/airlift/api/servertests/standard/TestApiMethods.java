package io.airlift.api.servertests.standard;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.airlift.api.ApiResourceVersion;
import io.airlift.api.servertests.ServerTestBase;
import io.airlift.bootstrap.ApplicationConfigurationException;
import io.airlift.http.client.Request;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.json.JsonCodec;
import io.airlift.json.JsonCodecFactory;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static io.airlift.api.internals.Strings.camelCase;
import static io.airlift.api.servertests.standard.BaggageType.EXTRA_SOFT;
import static io.airlift.api.servertests.standard.BaggageType.ULTRA_LUXURIOUS;
import static io.airlift.api.servertests.standard.BaggageType.VERY_HARD;
import static io.airlift.http.client.HttpStatus.BAD_REQUEST;
import static io.airlift.http.client.HttpStatus.NOT_FOUND;
import static io.airlift.http.client.HttpStatus.NO_CONTENT;
import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.Request.Builder.preparePut;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.json.JsonCodec.jsonCodec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestApiMethods
        extends ServerTestBase
{
    private static final JsonCodec<Thing> THING_JSON_CODEC = jsonCodec(Thing.class);
    private static final JsonCodec<NewThing> NEW_THING_JSON_CODEC = jsonCodec(NewThing.class);
    private static final JsonCodec<NewerThing> NEWER_THING_JSON_CODEC = jsonCodec(NewerThing.class);
    private static final JsonCodec<Name> NAME_JSON_CODEC = jsonCodec(Name.class);

    private final JsonCodec<PolyResource> polyResourceJsonCodec;
    private final JsonCodec<PolyResourceResult> polyResourceResultJsonCodec;

    public TestApiMethods()
    {
        super(StandardService.class, builder -> builder.addIdLookupBinding(LookupId.class, binding -> binding.to(LookupHandler.class)));

        // we need to use the injected object mapper which has all the Poly resource modules
        JsonCodecFactory jsonCodecFactory = new JsonCodecFactory(() -> objectMapper);
        polyResourceJsonCodec = jsonCodecFactory.jsonCodec(PolyResource.class);
        polyResourceResultJsonCodec = jsonCodecFactory.jsonCodec(PolyResourceResult.class);
    }

    @Test
    public void testGetThing()
    {
        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/thing/12345").build();
        Request request = prepareGet().setUri(uri).build();
        Thing thing = httpClient.execute(request, createJsonResponseHandler(THING_JSON_CODEC));
        assertThat(thing).isEqualTo(new Thing(new ApiResourceVersion(1), new ThingId("12345"), "dummy", 10, Optional.of("code")));
    }

    @Test
    public void testNoPayloadCreateSucceeds()
    {
        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1").build();
        Request request = preparePost().setUri(uri).build();
        StatusResponse response = httpClient.execute(request, createStatusResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(204);
    }

    @Test
    public void testUpdateThing()
    {
        Thing thing = new Thing(new ApiResourceVersion(1), new ThingId("12345"), "dummy", 10, Optional.of("code"));
        genericUpdateTest(thing, thing.thingId(), THING_JSON_CODEC, 204);
    }

    @Test
    public void testUpdateWithUnparsableJsonThing()
    {
        String invalidJson = "this is definitely not json";

        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/thing/{thingId}").build(new ThingId("12345"));
        Request request = preparePut()
                .setUri(uri)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .setBodyGenerator(createStaticBodyGenerator(invalidJson.getBytes()))
                .build();
        StringResponse response = httpClient.execute(request, createStringResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getBody()).contains("Unrecognized token 'this': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')");
    }

    @Test
    public void testUpdateWithInvalidThingId()
    {
        String thingInvalidJsonStringified = """
                {
                  "syncToken" : "1",
                  "thingId" : 123,
                  "name" : "dummy",
                  "qty" : 10,
                  "code" : "code"
                }
                """;

        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/thing/{thingId}").build(new ThingId("12345"));
        Request request = preparePut()
                .setUri(uri)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .setBodyGenerator(createStaticBodyGenerator(thingInvalidJsonStringified.getBytes()))
                .build();
        StringResponse response = httpClient.execute(request, createStringResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getBody()).contains("Could not parse entity: ThingId");
    }

    @Test
    public void testUpdateWithInvalidSyncToken()
    {
        String thingInvalidJsonStringified = """
                {
                  "syncToken" : 1,
                  "thingId" : "12345",
                  "name" : "dummy",
                  "qty" : 10,
                  "code" : "code"
                }
                """;

        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/thing/{thingId}").build(new ThingId("12345"));
        Request request = preparePut()
                .setUri(uri)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .setBodyGenerator(createStaticBodyGenerator(thingInvalidJsonStringified.getBytes()))
                .build();
        StringResponse response = httpClient.execute(request, createStringResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getBody()).contains("Could not parse entity: ApiResourceVersion");
    }

    @Test
    public void testUpdateWithNewerEntity()
    {
        NewerThing newerThing = new NewerThing(new ApiResourceVersion(1), new ThingId("6789"), "hey", 101, Optional.of("code"), Optional.of("foo"));
        genericUpdateTest(newerThing, newerThing.id(), NEWER_THING_JSON_CODEC, 400);
    }

    @Test
    public void testUpdateWithNulls()
    {
        Thing thing = new Thing(new ApiResourceVersion(1), new ThingId("2468"), null, 101, Optional.of("code"));
        genericUpdateTest(thing, thing.thingId(), THING_JSON_CODEC, 400);
    }

    @Test
    public void testCustomVerbMapping()
    {
        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/thing/12345:doesNotExist").build();
        Request request = prepareGet()
                .setUri(uri)
                .build();
        StatusResponse response = httpClient.execute(request, createStatusResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND.code());
    }

    @Test
    public void testCustomVerbEscaping()
    {
        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/thing/a%3Ab%3Ac:repeat").build();
        Request request = prepareGet()
                .setUri(uri)
                .build();
        Thing response = httpClient.execute(request, createJsonResponseHandler(THING_JSON_CODEC));
        assertThat(response.thingId()).isEqualTo(new ThingId("a:b:c"));
    }

    @Test
    public void testVoidReturn()
    {
        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/thing:void").build();
        NewThing neweThing = new NewThing("hey", 101, Optional.of("code"));
        Request request = preparePost()
                .setUri(uri)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .setBodyGenerator(jsonBodyGenerator(NEW_THING_JSON_CODEC, neweThing))
                .build();
        StatusResponse response = httpClient.execute(request, createStatusResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(NO_CONTENT.code());
    }

    @Test
    public void testLimitedValuesEnumId()
    {
        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/baggage/%s/thing/12345".formatted(camelCase(EXTRA_SOFT.name()))).build();
        Request request = prepareGet().setUri(uri).build();
        Thing thing = httpClient.execute(request, createJsonResponseHandler(THING_JSON_CODEC));
        assertThat(thing.name()).isEqualTo(EXTRA_SOFT.name());

        uri = UriBuilder.fromUri(baseUri).path("public/api/v1/baggage/%s/thing/12345".formatted(camelCase(VERY_HARD.name()))).build();
        request = prepareGet().setUri(uri).build();
        thing = httpClient.execute(request, createJsonResponseHandler(THING_JSON_CODEC));
        assertThat(thing.name()).isEqualTo(VERY_HARD.name());

        uri = UriBuilder.fromUri(baseUri).path("public/api/v1/baggage/%s/thing/12345".formatted(camelCase(ULTRA_LUXURIOUS.name()))).build();
        request = prepareGet().setUri(uri).build();
        StatusResponse response = httpClient.execute(request, createStatusResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND.code());

        uri = UriBuilder.fromUri(baseUri).path("public/api/v1/baggage/%s/thing/12345:ultra".formatted(camelCase(ULTRA_LUXURIOUS.name()))).build();
        request = prepareGet().setUri(uri).build();
        thing = httpClient.execute(request, createJsonResponseHandler(THING_JSON_CODEC));
        assertThat(thing.name()).isEqualTo(ULTRA_LUXURIOUS.name());
    }

    @Test
    public void testStringIdGet()
    {
        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/name/itsANameInnit").build();
        Request request = prepareGet().setUri(uri).build();
        Name name = httpClient.execute(request, createJsonResponseHandler(NAME_JSON_CODEC));
        assertThat(name.nameId().toString()).isEqualTo("itsANameInnit");
    }

    @Test
    public void testIdLookup()
    {
        // test with encoded value that matches prefix to ensure that this works
        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/thing/name=name%3Dbuilt:name").build();
        Request request = prepareGet()
                .setUri(uri)
                .build();
        Thing response = httpClient.execute(request, createJsonResponseHandler(THING_JSON_CODEC));
        assertThat(response.name()).isEqualTo("name=built-name");

        // test with encoded value that matches prefix but without a lookup to ensure that this works
        uri = UriBuilder.fromUri(baseUri).path("public/api/v1/thing/name%3Dbuilt:name").build();
        request = prepareGet()
                .setUri(uri)
                .build();
        response = httpClient.execute(request, createJsonResponseHandler(THING_JSON_CODEC));
        assertThat(response.name()).isEqualTo("name=built");

        // test with empty lookup value
        uri = UriBuilder.fromUri(baseUri).path("public/api/v1/thing/name=:name").build();
        request = prepareGet()
                .setUri(uri)
                .build();
        int statusCode = httpClient.execute(request, createStatusResponseHandler()).getStatusCode();
        assertThat(statusCode).isEqualTo(NOT_FOUND.code());
    }

    @Test
    public void testMissingIdBinding()
    {
        // StandardService, by default, does not bind LookupHandler
        assertThatThrownBy(() -> new ServerTestBase(StandardService.class) {}).isInstanceOf(ApplicationConfigurationException.class);
    }

    @Test
    public void testPolyResources()
    {
        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/poly").build();

        PolyType polyType = new PolyType.TypeOne("one", "1");
        Request request = preparePost()
                .setUri(uri)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .setBodyGenerator(jsonBodyGenerator(polyResourceJsonCodec, new PolyResource(polyType)))
                .build();
        PolyResourceResult result = httpClient.execute(request, createJsonResponseHandler(polyResourceResultJsonCodec));
        assertThat(result.polyId().toString()).isEqualTo("TypeOne");

        polyType = new PolyType.TypeTwo(Instant.now(), 2);
        request = preparePost()
                .setUri(uri)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .setBodyGenerator(jsonBodyGenerator(polyResourceJsonCodec, new PolyResource(polyType)))
                .build();
        result = httpClient.execute(request, createJsonResponseHandler(polyResourceResultJsonCodec));
        assertThat(result.polyId().toString()).isEqualTo("TypeTwo");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testPolyResourcesWithBadKey()
            throws JsonProcessingException
    {
        String badJson = """
                {
                  "poly": {
                    "typeKey": "thisAintOneOfEm",
                    "name": "one",
                    "value": "1"
                  }
                }
                """;

        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/poly").build();

        Request request = preparePost()
                .setUri(uri)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .setBodyGenerator(createStaticBodyGenerator(badJson, StandardCharsets.UTF_8))
                .build();
        StringResponse response = httpClient.execute(request, createStringResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST.code());
    }

    private <T> void genericUpdateTest(T thing, ThingId thingId, JsonCodec<T> jsonCodec, int expectedResponseCode)
    {
        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/thing/{thingId}").build(thingId);
        Request request = preparePut()
                .setUri(uri)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .setBodyGenerator(jsonBodyGenerator(jsonCodec, thing))
                .build();
        StatusResponse response = httpClient.execute(request, createStatusResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(expectedResponseCode);
    }
}
