package com.proofpoint.json;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;

public class MyPojoSerializer implements JsonSerializerHelper<MyPojo>
{
    @Override
    public void writeObject(JsonUtilFactory factory, JsonGenerator generator, MyPojo object) throws Exception
    {
        generator.writeStartObject();
        generator.writeStringField(FieldNames.STRING, object.getStr());
        generator.writeNumberField(FieldNames.INT, object.getI());
        generator.writeNumberField(FieldNames.DOUBLE, object.getD());
        generator.writeNumberField(FieldNames.LONG, object.getL());
        generator.writeEndObject();
    }

    @Override
    public MyPojo readObject(JsonUtilFactory factory, JsonParser parser) throws Exception
    {
        JsonNode node = parser.readValueAsTree();
        return new MyPojo
        (
            node.get(FieldNames.STRING).getTextValue(),
            node.get(FieldNames.INT).getIntValue(),
            node.get(FieldNames.LONG).getLongValue(),
            node.get(FieldNames.DOUBLE).getDoubleValue()
        );
    }
}
