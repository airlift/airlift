package io.airlift.json;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "@type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Car.class, name = "car"),
        @JsonSubTypes.Type(value = Truck.class, name = "truck")})
public interface Vehicle
{
    static void validateVehicleJsonCodec(JsonCodec<Vehicle> jsonCodec)
    {
        Vehicle expected = new Car("bmw");

        String json = jsonCodec.toJson(expected);
        assertEquals(jsonCodec.fromJson(json), expected);
        assertTrue(json.contains("\"@type\" : \"car\""));

        byte[] bytes = jsonCodec.toJsonBytes(expected);
        assertEquals(jsonCodec.fromJson(bytes), expected);

        expected = new Truck("volvo");

        json = jsonCodec.toJson(expected);
        assertEquals(jsonCodec.fromJson(json), expected);
        assertTrue(json.contains("\"@type\" : \"truck\""));

        bytes = jsonCodec.toJsonBytes(expected);
        assertEquals(jsonCodec.fromJson(bytes), expected);
    }

    static void validateVehicleListJsonCodec(JsonCodec<List<Vehicle>> jsonCodec)
    {
        ImmutableList<Vehicle> expected = ImmutableList.of(
                new Car("bmw"),
                new Truck("volvo"));

        String json = jsonCodec.toJson(expected);
        assertEquals(jsonCodec.fromJson(json), expected);

        byte[] bytes = jsonCodec.toJsonBytes(expected);
        assertEquals(jsonCodec.fromJson(bytes), expected);
    }

    static void validateVehicleMapJsonCodec(JsonCodec<Map<String, Vehicle>> jsonCodec)
    {
        ImmutableMap<String, Vehicle> expected = ImmutableMap.<String, Vehicle>builder()
                .put("bmw", new Car("bmw"))
                .put("volvo", new Truck("volvo"))
                .build();

        String json = jsonCodec.toJson(expected);
        assertEquals(jsonCodec.fromJson(json), expected);

        byte[] bytes = jsonCodec.toJsonBytes(expected);
        assertEquals(jsonCodec.fromJson(bytes), expected);
    }
}
