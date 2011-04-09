package com.proofpoint.experimental.json;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.testng.annotations.Test;

public class TestJsonModule
{
    @Test
    public void test()
            throws Exception
    {
        Injector injector = Guice.createInjector(new JsonModule());
        JsonCodecFactory codecFactory = injector.getInstance(JsonCodecFactory.class);

        Person.validatePersonJsonCodec(codecFactory.jsonCodec(Person.class));
        Person.validatePersonListJsonCodec(codecFactory.listJsonCodec(Person.class));
        Person.validatePersonMapJsonCodec(codecFactory.mapJsonCodec(String.class, Person.class));
    }
}
