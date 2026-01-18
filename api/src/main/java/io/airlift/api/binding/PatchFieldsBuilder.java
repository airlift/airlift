package io.airlift.api.binding;

import com.google.inject.Inject;
import io.airlift.api.ApiPatch;
import tools.jackson.core.JsonParser;
import tools.jackson.core.TreeNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static io.airlift.api.responses.ApiException.badRequest;
import static java.util.Objects.requireNonNull;
import static tools.jackson.core.StreamReadFeature.AUTO_CLOSE_SOURCE;

public class PatchFieldsBuilder
{
    private final ObjectMapper objectMapper;

    @Inject
    public PatchFieldsBuilder(ObjectMapper objectMapper)
    {
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null")
                .rebuild()
                // Do not close underlying stream after mapping
                .disable(AUTO_CLOSE_SOURCE)
                .build();
    }

    public <T> ApiPatch<T> buildPatchFields(InputStream entityStream)
    {
        try {
            JsonParser jsonParser = objectMapper.createParser(entityStream);

            TreeNode treeNode = jsonParser.readValueAsTree();

            Map<String, Function<Type, Object>> fields = new HashMap<>();
            buildFields(treeNode, fields);

            return new ApiPatch<>(fields);
        }
        catch (Exception e) {
            return JaxrsMapper.mapException(e);
        }
    }

    private void buildFields(TreeNode node, Map<String, Function<Type, Object>> fields)
    {
        for (String name : node.propertyNames()) {
            TreeNode child = node.get(name);
            fields.put(name, type -> {
                try {
                    return objectMapper.treeToValue(child, objectMapper.getTypeFactory().constructType(type));
                }
                catch (Exception e) {
                    throw badRequest("Could not construct value for: " + name);
                }
            });
        }
    }
}
