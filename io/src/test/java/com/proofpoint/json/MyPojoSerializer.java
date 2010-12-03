package com.proofpoint.json;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;

public class MyPojoSerializer implements JsonSerializer<MyPojo>
{
    @Override
    public void writeObject(JsonSerializeWriter writer, JsonGenerator generator, MyPojo object) throws Exception
    {
        generator.writeStringField(FieldNames.STRING, object.getStr());
        generator.writeNumberField(FieldNames.INT, object.getI());
        generator.writeNumberField(FieldNames.DOUBLE, object.getD());
        generator.writeNumberField(FieldNames.LONG, object.getL());
    }

    @Override
    public MyPojo readObject(JsonSerializeReader reader, JsonNode node) throws Exception
    {
        return new MyPojo
        (
            node.get(FieldNames.STRING).getTextValue(),
            node.get(FieldNames.INT).getIntValue(),
            node.get(FieldNames.LONG).getLongValue(),
            node.get(FieldNames.DOUBLE).getDoubleValue()
        );
    }
}
