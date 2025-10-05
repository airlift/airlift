package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableList;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.util.List;

import static tools.jackson.core.JsonToken.START_ARRAY;
import static tools.jackson.core.JsonToken.VALUE_STRING;

@JsonDeserialize(using = SkillResources.Deserializer.class)
public sealed interface SkillResources
{
    record Files(@JsonValue List<SkillResource> files)
            implements SkillResources
    {
        public Files
        {
            files = ImmutableList.copyOf(files);
        }
    }

    enum Dynamic
            implements SkillResources
    {
        DYNAMIC;

        @JsonValue
        public String value()
        {
            return "dynamic";
        }
    }

    final class Deserializer
            extends StdDeserializer<SkillResources>
    {
        public Deserializer()
        {
            super(SkillResources.class);
        }

        @Override
        public SkillResources deserialize(JsonParser parser, DeserializationContext context)
        {
            if (parser.hasToken(VALUE_STRING) && parser.getString().equals(Dynamic.DYNAMIC.value())) {
                return Dynamic.DYNAMIC;
            }
            if (parser.hasToken(START_ARRAY)) {
                List<SkillResource> files = context.readValue(parser, context.getTypeFactory().constructCollectionType(List.class, SkillResource.class));
                return new Files(files);
            }
            return (SkillResources) context.handleUnexpectedToken(SkillResources.class, parser);
        }
    }
}
