package io.airlift.json.isolated;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.airlift.json.JsonCodec;
import io.airlift.json.JsonCodecBinder;
import io.airlift.json.JsonModule;
import io.airlift.json.Person;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static io.airlift.json.JsonCodecBinder.jsonCodecBinder;
import static org.testng.Assert.assertNotNull;

public class TestJsonCodecBinder
{
    @Inject
    protected JsonCodec<Person> personJsonCodec;
    @Inject
    protected JsonCodec<List<Person>> personListJsonCodec;
    @Inject
    protected JsonCodec<Map<String, Person>> personMapJsonCodec;

    @Test
    public void test()
            throws Exception
    {
        Injector injector = Guice.createInjector(new JsonModule(),
                new Module()
                {
                    public void configure(Binder binder)
                    {
                        JsonCodecBinder codecBinder = jsonCodecBinder(binder);
                        codecBinder.bindJsonCodec(Person.class);
                        codecBinder.bindListJsonCodec(Person.class);
                        codecBinder.bindMapJsonCodec(String.class, Person.class);
                    }
                });

        injector.injectMembers(this);

        assertNotNull(personJsonCodec);
        assertNotNull(personListJsonCodec);
        assertNotNull(personMapJsonCodec);

        Person.validatePersonJsonCodec(personJsonCodec);
        Person.validatePersonListJsonCodec(personListJsonCodec);
        Person.validatePersonMapJsonCodec(personMapJsonCodec);
    }
}
