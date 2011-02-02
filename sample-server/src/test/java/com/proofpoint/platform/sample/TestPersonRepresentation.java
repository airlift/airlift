package com.proofpoint.platform.sample;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.proofpoint.experimental.json.JsonCodec;
import com.proofpoint.experimental.json.JsonCodecBuilder;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestPersonRepresentation
{
    private final JsonCodec<PersonRepresentation> codec = new JsonCodecBuilder().build(PersonRepresentation.class);

    // TODO: add equivalence test

    @Test
    public void testJsonRoundTrip()
    {
        PersonRepresentation expected = new PersonRepresentation("alice@example.com", "Alice", null);
        String json = codec.toJson(expected);
        PersonRepresentation actual = codec.fromJson(json);
        assertEquals(actual, expected);
    }

    @Test
    public void testJsonDecode()
            throws Exception
    {
        PersonRepresentation expected = new PersonRepresentation("foo@example.com", "Mr Foo", null);

        String json = Resources.toString(Resources.getResource("single.json"), Charsets.UTF_8);
        PersonRepresentation actual = codec.fromJson(json);

        assertEquals(actual, expected);
    }
}
