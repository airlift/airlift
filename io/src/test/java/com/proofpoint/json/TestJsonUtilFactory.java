package com.proofpoint.json;

import com.google.common.collect.Sets;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;

public class TestJsonUtilFactory
{
    @Test
    public void testWithGuice() throws Exception
    {
        Injector injector = Guice.createInjector
        (
            new Module()
            {
                @Override
                public void configure(Binder binder)
                {
                    Multibinder<JsonSerializationMapping>   multibinder = Multibinder.newSetBinder(binder, JsonSerializationMapping.class);
                    multibinder.addBinding().toInstance(JsonSerializationMapping.make(MyPojo.class, new MyPojoSerializer()));
                    multibinder.addBinding().toInstance(JsonSerializationMapping.make(MyContainment.class, new MyContainmentSerializer()));

                    binder.bind(JsonUtilFactory.class).in(Scopes.SINGLETON);
                }
            }
        );

        JsonUtilFactory factory = injector.getInstance(JsonUtilFactory.class);
        MyPojo              p = new MyPojo("yo", 1, 10, 10.20);
        byte[]              bytes = factory.serialize(p);
        assertEquals(new String(bytes), "{\"stringFieldName\":\"yo\",\"intFieldName\":1,\"doubleFieldName\":10.2,\"longFieldName\":10}");
        MyPojo              checkP = factory.deserialize(MyPojo.class, bytes);
        assertEquals(p, checkP);
    }

    @Test
    public void testSimple() throws Exception
    {
        Set<JsonSerializationMapping> mappings = Sets.newHashSet(JsonSerializationMapping.make(MyPojo.class, new MyPojoSerializer()));
        JsonUtilFactory factory = new JsonUtilFactory(mappings);

        MyPojo                  p = new MyPojo("a", 1, 2, 3.4);
        byte[]                  bytes = factory.serialize(p);
        MyPojo                  readP = factory.deserialize(MyPojo.class, bytes);

        assertEquals(p, readP);
    }

    @Test
    public void testCollection() throws Exception
    {
        Set<JsonSerializationMapping>   mappings = Sets.newHashSet(JsonSerializationMapping.make(MyPojo.class, new MyPojoSerializer()));
        JsonUtilFactory factory = new JsonUtilFactory(mappings);

        List<MyPojo>            pList = new ArrayList<MyPojo>();
        int                     counter = 0;
        for ( int i = 0; i < 10; ++i )
        {
            MyPojo                  p = new MyPojo(Integer.toString(counter++), counter++, counter++, counter++ / 13.5);
            pList.add(p);
        }

        byte[]                  bytes = factory.serialize(pList);
        Collection<MyPojo>      readPList = factory.deserializeCollection(MyPojo.class, bytes);

        assertEquals(pList, readPList);
    }

    @Test
    public void testContainment() throws Exception
    {
        Set<JsonSerializationMapping> mappings = Sets.newHashSet
        (
            JsonSerializationMapping.make(MyPojo.class, new MyPojoSerializer()),
            JsonSerializationMapping.make(MyContainment.class, new MyContainmentSerializer())
        );
        JsonUtilFactory factory = new JsonUtilFactory(mappings);

        MyContainment           c = new MyContainment(new MyPojo("a", 1, 2, 3.4), "foo");

        byte[]                  bytes = factory.serialize(c);
        MyContainment           readC = factory.deserialize(MyContainment.class, bytes);

        assertEquals(c, readC);
    }
}
