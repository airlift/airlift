package io.airlift.mcp.operations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.json.JsonMapperProvider;
import io.airlift.mcp.messages.MessageWriter;
import io.airlift.mcp.model.DiscoverResult;
import io.airlift.mcp.model.Implementation;
import io.airlift.mcp.model.InitializeResult.ServerCapabilities;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Constants.METADATA_SERVER_INFO;
import static io.airlift.mcp.model.ResultType.COMPLETE;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class TestOperations
{
    private static final JsonMapper JSON_MAPPER = new JsonMapperProvider().get();

    @Test
    public void testWriteResultAddsServerInfoMetadata()
            throws Exception
    {
        JsonNode responseJson = writeResult(Optional.of(ImmutableMap.of("existing", "value")));
        JsonNode responseMetadata = responseJson.at("/result/_meta");
        JsonNode responseServerInfo = responseMetadata.get(METADATA_SERVER_INFO);
        assertThat(responseJson.at("/result/serverInfo").isMissingNode()).isTrue();
        assertThat(responseMetadata.get("existing").textValue()).isEqualTo("value");
        assertThat(responseServerInfo.get("name").textValue()).isEqualTo("test server");
        assertThat(responseServerInfo.get("version").textValue()).isEqualTo("1");
    }

    @Test
    public void testWriteResultCreatesMetadata()
            throws Exception
    {
        JsonNode responseJson = writeResult(Optional.empty());

        JsonNode responseMetadata = responseJson.at("/result/_meta");
        assertThat(responseMetadata.isObject()).isTrue();
        assertThat(responseMetadata.get(METADATA_SERVER_INFO).get("name").textValue()).isEqualTo("test server");
    }

    @Test
    public void testWriteResultRejectsNonObject()
    {
        MessageWriter messageWriter = mock(MessageWriter.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        assertThatThrownBy(() -> Operations.writeResult(
                JSON_MAPPER,
                messageWriter,
                response,
                123,
                "invalid",
                ImmutableMap.of(METADATA_SERVER_INFO, new Implementation("test server", "1"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Result must serialize to a JSON object");

        verify(response).setStatus(SC_OK);
        verifyNoMoreInteractions(response);
        verifyNoInteractions(messageWriter);
    }

    private static JsonNode writeResult(Optional<Map<String, Object>> metadata)
            throws Exception
    {
        MessageWriter messageWriter = mock(MessageWriter.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        DiscoverResult result = new DiscoverResult(COMPLETE, ImmutableList.of(), new ServerCapabilities(), Optional.empty(), metadata);

        Operations.writeResult(
                JSON_MAPPER,
                messageWriter,
                response,
                123,
                result,
                ImmutableMap.of(METADATA_SERVER_INFO, new Implementation("test server", "1")));

        ArgumentCaptor<String> responseBody = ArgumentCaptor.captor();
        verify(response).setStatus(SC_OK);
        verify(messageWriter).write(responseBody.capture());
        verify(messageWriter).flush();
        verifyNoMoreInteractions(response, messageWriter);

        return JSON_MAPPER.readTree(responseBody.getValue());
    }
}
