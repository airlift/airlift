package com.proofpoint.json;

import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestJsonSerializer
{
    @Test
    public void testAutoRegisteredTypes() throws Exception
    {
        String          testValue = "A is A";
        byte[]          bytes = new JsonSerializeWriter(JsonSerializeWriter.Mode.SIMPLE).writeObject(testValue).close();
        String          resultValue = new JsonSerializeReader(bytes).readObject(String.class);
        assertEquals(testValue, resultValue);
    }

    @Test
    public void testUnregistered() throws Exception
    {
        JsonSerializeWriter     writer = new JsonSerializeWriter();
        try
        {
            writer.writeObject(new Date());
            fail("Writing unregistered object types should throw an exception");
        }
        catch ( Exception e )
        {
            // correct
        }
    }

    @Test
    public void testSimple() throws Exception
    {
        JsonSerializeRegistry.register(MyPojo.class, MyPojoSerializer.class);

        MyPojo                  p = new MyPojo("a", 1, 2, 3.4);

        JsonSerializeWriter     writer = new JsonSerializeWriter();
        writer.writeObject(p);
        byte[]                  bytes = writer.close();

        JsonSerializeReader     reader = new JsonSerializeReader(bytes);
        MyPojo                  readP = reader.readObject(MyPojo.class);

        assertEquals(p, readP);
    }

    @Test
    public void testCollection() throws Exception
    {
        JsonSerializeRegistry.register(MyPojo.class, MyPojoSerializer.class);

        List<MyPojo>            pList = new ArrayList<MyPojo>();
        int                     counter = 0;
        for ( int i = 0; i < 10; ++i )
        {
            MyPojo                  p = new MyPojo(Integer.toString(counter++), counter++, counter++, counter++ / 13.5);
            pList.add(p);
        }

        JsonSerializeWriter     writer = new JsonSerializeWriter();
        writer.writeObjectCollection("list", pList);
        byte[]                  bytes = writer.close();

        JsonSerializeReader     reader = new JsonSerializeReader(bytes);
        List<MyPojo>            readPList = reader.readObjectCollection("list", MyPojo.class);

        assertEquals(pList, readPList);
    }

    @Test
    public void testContainment() throws Exception
    {
        JsonSerializeRegistry.register(MyPojo.class, MyPojoSerializer.class);
        JsonSerializeRegistry.register(MyContainment.class, MyContainmentSerializer.class);

        MyContainment           c = new MyContainment(new MyPojo("a", 1, 2, 3.4), "foo");

        byte[]                  bytes = new JsonSerializeWriter().writeObject(c).close();

        MyContainment           readC = new JsonSerializeReader(bytes).readObject(MyContainment.class);

        assertEquals(c, readC);
    }
}
