/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.json;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;

public class TestSmileCodecFactory
{
    private final SmileCodecFactory codecFactory = new SmileCodecFactory();

    @Test
    public void testSmileCodec()
    {
        Codec<Person> smileCodec = codecFactory.codec(Person.class);
        Person expected = new Person().setName("person-1").setRocks(true);
        byte[] smile = smileCodec.toBytes(expected);
        assertEquals(smileCodec.fromBytes(smile), expected);
    }

    @Test
    public void testListSmileCodec()
    {
        Codec<List<Person>> smileCodec = codecFactory.listCodec(Person.class);
        this.validatePersonListSmileCodec(smileCodec);
    }

    @Test
    public void testTypeTokenList()
    {
        Codec<List<Person>> smileCodec = codecFactory.codec(new TypeToken<List<Person>>() {}.getType());
        this.validatePersonListSmileCodec(smileCodec);
    }

    @Test
    public void testListNullValues()
    {
        Codec<List<String>> smileCodec = codecFactory.listCodec(String.class);

        List<String> list = new ArrayList<>();
        list.add(null);
        list.add("abc");

        assertEquals(smileCodec.fromBytes(smileCodec.toBytes(list)), list);
    }

    @Test
    public void testMapSmileCodec()
    {
        Codec<Map<String, Person>> smileCodec = codecFactory.mapCodec(String.class, Person.class);
        validatePersonMapSmileCodec(smileCodec);
    }

    @Test
    public void testMapSmileCodecFromSmileCodec()
    {
        Codec<Map<String, Person>> smileCodec = codecFactory.mapCodec(String.class, codecFactory.codec(Person.class));
        validatePersonMapSmileCodec(smileCodec);
    }

    @Test
    public void testTypeLiteralMap()
    {
        Codec<Map<String, Person>> smileCodec = codecFactory.codec(new TypeToken<Map<String, Person>>() {}.getType());
        validatePersonMapSmileCodec(smileCodec);
    }

    @Test
    public void testMapNullValues()
    {
        Codec<Map<String, String>> smileCodec = codecFactory.mapCodec(String.class, String.class);

        Map<String, String> map = new HashMap<>();
        map.put("x", null);
        map.put("y", "abc");

        assertEquals(smileCodec.fromBytes(smileCodec.toBytes(map)), map);
    }

    @Test
    public void testImmutableSmileCodec()
    {
        Codec<ImmutablePerson> smileCodec = codecFactory.codec(ImmutablePerson.class);
        ImmutablePerson expected = new ImmutablePerson("person-1", true);
        assertEquals(smileCodec.fromBytes(smileCodec.toBytes(expected)), expected);
    }

    @Test
    public void testImmutableListSmileCodec()
    {
        Codec<List<ImmutablePerson>> smileCodec = codecFactory.listCodec(ImmutablePerson.class);
        validateImmutablePersonListSmileCodec(smileCodec);
    }

    @Test
    public void testImmutableListSmileCodecFromSmileCodec()
    {
        Codec<List<ImmutablePerson>> smileCodec = codecFactory.listCodec(codecFactory.codec(ImmutablePerson.class));
        validateImmutablePersonListSmileCodec(smileCodec);
    }

    @Test
    public void testImmutableTypeTokenList()
    {
        Codec<List<ImmutablePerson>> smileCodec = codecFactory.codec(new TypeToken<List<ImmutablePerson>>() {}.getType());
        validateImmutablePersonListSmileCodec(smileCodec);
    }

    @Test
    public void testImmutableMapSmileCodec()
    {
        Codec<Map<String, ImmutablePerson>> smileCodec = codecFactory.mapCodec(String.class, ImmutablePerson.class);
        validateImmutablePersonMapSmileCodec(smileCodec);
    }

    @Test
    public void testImmutableMapSmileCodecFromSmileCodec()
    {
        Codec<Map<String, ImmutablePerson>> smileCodec = codecFactory.mapCodec(String.class, codecFactory.codec(ImmutablePerson.class));
        validateImmutablePersonMapSmileCodec(smileCodec);
    }

    @Test
    public void testImmutableTypeTokenMap()
    {
        Codec<Map<String, ImmutablePerson>> smileCodec = codecFactory.codec(new TypeToken<Map<String, ImmutablePerson>>() {}.getType());
        validateImmutablePersonMapSmileCodec(smileCodec);
    }

    private void validatePersonListSmileCodec(Codec<List<Person>> smileCodec)
    {
        List<Person> expected = ImmutableList.of(
                new Person().setName("person-1").setRocks(true),
                new Person().setName("person-2").setRocks(true),
                new Person().setName("person-3").setRocks(true));

        byte[] smileBytes = smileCodec.toBytes(expected);
        List<Person> actual = smileCodec.fromBytes(smileBytes);
        assertEquals(actual, expected);
    }

    private void validatePersonMapSmileCodec(Codec<Map<String, Person>> smileCodec)
    {
        Map<String, Person> expected = ImmutableMap.<String, Person>builder()
                .put("person-1", new Person().setName("person-1").setRocks(true))
                .put("person-2", new Person().setName("person-2").setRocks(true))
                .put("person-3", new Person().setName("person-3").setRocks(true))
                .build();

        byte[] smileBytes = smileCodec.toBytes(expected);
        Map<String, Person> actual = smileCodec.fromBytes(smileBytes);
        assertEquals(actual, expected);
    }

    private void validateImmutablePersonMapSmileCodec(Codec<Map<String, ImmutablePerson>> smileCodec)
    {
        Map<String, ImmutablePerson> expected = ImmutableMap.<String, ImmutablePerson>builder()
                .put("person-1", new ImmutablePerson("person-1", true))
                .put("person-2", new ImmutablePerson("person-2", true))
                .put("person-3", new ImmutablePerson("person-3", true))
                .build();

        byte[] smileBytes = smileCodec.toBytes(expected);
        Map<String, ImmutablePerson> actual = smileCodec.fromBytes(smileBytes);
        assertEquals(actual, expected);
    }

    private void validateImmutablePersonListSmileCodec(Codec<List<ImmutablePerson>> smileCodec)
    {
        List<ImmutablePerson> expected = ImmutableList.of(
                new ImmutablePerson("person-1", true),
                new ImmutablePerson("person-2", true),
                new ImmutablePerson("person-3", true));

        byte[] smileBytes = smileCodec.toBytes(expected);
        List<ImmutablePerson> actual = smileCodec.fromBytes(smileBytes);
        assertEquals(actual, expected);
    }
}
