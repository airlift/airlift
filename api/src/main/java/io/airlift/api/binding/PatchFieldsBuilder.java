package io.airlift.api.binding;

import com.google.inject.Inject;
import io.airlift.api.ApiPatch;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import static io.airlift.api.responses.ApiException.badRequest;
import static java.util.Objects.requireNonNull;
import static tools.jackson.core.StreamReadFeature.AUTO_CLOSE_SOURCE;

public class PatchFieldsBuilder
{
    private final JsonMapper jsonMapper;

    @Inject
    public PatchFieldsBuilder(JsonMapper jsonMapper)
    {
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null")
                .rebuild()
                // Do not close underlying stream after mapping
                .disable(AUTO_CLOSE_SOURCE)
                .build();
    }

    public <T> ApiPatch<T> buildPatchFields(InputStream entityStream)
    {
        try (JsonParser jsonParser = jsonMapper.createParser(entityStream)) {
            Map<String, Function<Type, Object>> fields = new LinkedHashMap<>();
            buildFields(jsonParser.readValueAsTree(), fields);
            return new ApiPatch<>(fields);
        }
        catch (Exception e) {
            return JaxrsMapper.mapException(e);
        }
    }

    private void buildFields(JsonNode node, Map<String, Function<Type, Object>> fields)
    {
        for (String name : node.propertyNames()) {
            JsonNode child = node.get(name);
            fields.put(name, type -> {
                try {
                    return jsonMapper.treeToValue(child, jsonMapper.constructType(type));
                }
                catch (Exception e) {
                    throw badRequest("Could not construct value for: " + name);
                }
            });
        }
    }
}
