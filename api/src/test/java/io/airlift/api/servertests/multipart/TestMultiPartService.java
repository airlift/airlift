package io.airlift.api.servertests.multipart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import io.airlift.api.servertests.ServerTestBase;
import io.airlift.http.client.Request;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.json.ObjectMapperProvider;
import jakarta.ws.rs.core.UriBuilder;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;

import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class TestMultiPartService
        extends ServerTestBase
{
    static final Instant INSTANT = Instant.now();

    private static final File testFile = Path.of(Resources.getResource("test-file.txt").getPath()).toFile();

    public TestMultiPartService()
    {
        super(MultiPartService.class);
    }

    @Test
    public void testMultiPartService()
            throws IOException
    {
        ObjectMapper objectMapper = new ObjectMapperProvider().get();

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addTextBody("part", objectMapper.writeValueAsString(MultiPartService.THE_PART), ContentType.APPLICATION_JSON);
        builder.addBinaryBody("file", testFile);
        HttpEntity entity = builder.build();

        StatusResponse response = doRequest(entity, "public/api/v1/part");
        assertThat(response.getStatusCode()).isEqualTo(204);
    }

    @Test
    public void testMultiPartServiceNoResource()
            throws IOException
    {
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addBinaryBody("file", testFile);
        HttpEntity entity = builder.build();

        StatusResponse response = doRequest(entity, "public/api/v1/part:noresource");
        assertThat(response.getStatusCode()).isEqualTo(204);
    }

    @Test
    public void testBadContent()
            throws IOException
    {
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addTextBody("name", "Ryan Giggs");
        builder.addTextBody("qty", "10", ContentType.APPLICATION_JSON);
        builder.addBinaryBody("file", testFile);
        builder.addTextBody("dateTime", "not a date time");
        HttpEntity entity = builder.build();

        StatusResponse response = doRequest(entity, "public/api/v1/part");
        assertThat(response.getStatusCode()).isEqualTo(400);
    }

    private StatusResponse doRequest(HttpEntity entity, String path)
            throws IOException
    {
        String content = new String(entity.getContent().readAllBytes(), UTF_8);

        URI uri = UriBuilder.fromUri(baseUri).path(path).build();
        Request request = preparePost()
                .setUri(uri)
                .setHeader("Content-Type", entity.getContentType().getValue())
                .setBodyGenerator(createStaticBodyGenerator(content, UTF_8))
                .build();
        return httpClient.execute(request, createStatusResponseHandler());
    }
}
