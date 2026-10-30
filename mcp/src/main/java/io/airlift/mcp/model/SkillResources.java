package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.List;

import static com.fasterxml.jackson.core.JsonToken.START_ARRAY;
import static com.fasterxml.jackson.core.JsonToken.VALUE_STRING;

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
                throws IOException
        {
            if (parser.hasToken(VALUE_STRING) && parser.getText().equals(Dynamic.DYNAMIC.value())) {
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
