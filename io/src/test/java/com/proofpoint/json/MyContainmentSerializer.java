package com.proofpoint.json;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;

public class MyContainmentSerializer implements JsonSerializer<MyContainment>
{
    @Override
    public void writeObject(JsonSerializeWriter writer, JsonGenerator generator, MyContainment object) throws Exception
    {
        writer.writeObject(FieldNames.POJO, object.getP());
        generator.writeStringField(FieldNames.STRING, object.getS());   // important - I want to test re-used field names        
    }

    @Override
    public MyContainment readObject(JsonSerializeReader reader, JsonNode node) throws Exception
    {
        return new MyContainment
        (
            reader.readObject(FieldNames.POJO, MyPojo.class),
            node.get(FieldNames.STRING).getTextValue()
        );
    }
}
