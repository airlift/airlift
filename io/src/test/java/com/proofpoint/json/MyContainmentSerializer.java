package com.proofpoint.json;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;

public class MyContainmentSerializer implements JsonSerializerHelper<MyContainment>
{
    @Override
    public void writeObject(JsonUtilFactory factory, JsonGenerator generator, MyContainment object) throws Exception
    {
        generator.writeStartObject();
        generator.writeObjectField(FieldNames.POJO, object.getP());
        generator.writeStringField(FieldNames.STRING, object.getS());   // important - I want to test re-used field names
        generator.writeEndObject();
    }

    @Override
    public MyContainment readObject(JsonUtilFactory factory, JsonParser parser) throws Exception
    {
        JsonNode node = parser.readValueAsTree();

        return new MyContainment
        (
            factory.deserializeContained(MyPojo.class, node.get(FieldNames.POJO)),
            node.get(FieldNames.STRING).getTextValue()
        );
    }
}
