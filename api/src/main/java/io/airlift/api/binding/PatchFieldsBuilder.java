package io.airlift.api.binding;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.inject.Inject;
import io.airlift.api.ApiPatch;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import static io.airlift.api.responses.ApiException.badRequest;
import static java.util.Objects.requireNonNull;

public class PatchFieldsBuilder
{
    private final JsonMapper jsonMapper;

    @Inject
    public PatchFieldsBuilder(JsonMapper jsonMapper)
    {
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
    }

    public <T> ApiPatch<T> buildPatchFields(InputStream entityStream)
    {
        try {
            JsonParser jsonParser = jsonMapper.createParser(entityStream);
            // Do not close underlying stream after mapping
            jsonParser.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
            TreeNode treeNode = jsonParser.readValueAsTree();

            Map<String, Function<Type, Object>> fields = new LinkedHashMap<>();
            buildFields(treeNode, fields);

            return new ApiPatch<>(fields);
        }
        catch (Exception e) {
            return JaxrsMapper.mapException(e);
        }
    }

    private void buildFields(TreeNode node, Map<String, Function<Type, Object>> fields)
    {
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            TreeNode child = node.get(name);
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
