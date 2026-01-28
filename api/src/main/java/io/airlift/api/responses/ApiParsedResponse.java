package io.airlift.api.responses;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import io.airlift.json.JsonCodec;
import io.airlift.json.ObjectMapperProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.json.JsonCodec.mapJsonCodec;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public record ApiParsedResponse(String message, Optional<String> description, List<String> fields)
{
    private static final JsonCodec<Map<String, Object>> CODEC = mapJsonCodec(String.class, Object.class);
    private static final ObjectMapper MAPPER = new ObjectMapperProvider().get();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

    public ApiParsedResponse
    {
        requireNonNull(message, "message is null");
        requireNonNull(description, "description is null");
        fields = ImmutableList.copyOf(fields);
    }

    public static Optional<ApiParsedResponse> parse(byte[] responseBytes)
    {
        try {
            Map<String, Object> values = CODEC.fromJson(responseBytes);
            if (values.containsKey("message")) {
                String message = String.valueOf(values.get("message"));
                Optional<String> description = values.containsKey("description") ? Optional.of(String.valueOf(values.get("description"))) : Optional.empty();
                List<String> fields;
                if (values.containsKey("fields")) {
                    try {
                        fields = MAPPER.convertValue(values.get("fields"), LIST_TYPE);
                    }
                    catch (IllegalArgumentException _) {
                        // ignore it
                        fields = ImmutableList.of();
                    }
                }
                else {
                    fields = ImmutableList.of();
                }
                return Optional.of(new ApiParsedResponse(message, description, fields));
            }
        }
        catch (IllegalArgumentException _) {
            // ignore
        }
        return Optional.empty();
    }

    public static Optional<ApiParsedResponse> parse(String response)
    {
        return parse(response.getBytes(UTF_8));
    }
}
