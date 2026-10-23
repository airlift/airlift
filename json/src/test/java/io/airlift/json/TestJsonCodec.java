/*
 * Copyright 2010 Proofpoint, Inc.
 *
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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.reflect.TypeToken;
import io.airlift.jackson.RecordAutoDetectModule;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonSerialize;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.time.Instant;
import java.time.Month;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.json.JsonCodec.listJsonCodec;
import static io.airlift.json.JsonCodec.mapJsonCodec;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tools.jackson.core.StreamReadConstraints.DEFAULT_MAX_STRING_LEN;

public class TestJsonCodec
{
    @Test
    public void testJsonCodec()
    {
        JsonCodec<Person> jsonCodec = jsonCodec(Person.class);

        Person.validatePersonJsonCodec(jsonCodec);

        JsonCodec<Vehicle> vehicleJsonCodec = jsonCodec(Vehicle.class);

        Vehicle.validateVehicleJsonCodec(vehicleJsonCodec);
    }

    @Test
    public void testListJsonCodec()
    {
        JsonCodec<List<Person>> jsonCodec = listJsonCodec(Person.class);

        Person.validatePersonListJsonCodec(jsonCodec);

        JsonCodec<List<Vehicle>> vehicleJsonCodec = listJsonCodec(Vehicle.class);

        Vehicle.validateVehicleListJsonCodec(vehicleJsonCodec);
    }

    @Test
    public void testListJsonCodecFromJsonCodec()
    {
        JsonCodec<List<Person>> jsonCodec = listJsonCodec(jsonCodec(Person.class));

        Person.validatePersonListJsonCodec(jsonCodec);

        JsonCodec<List<Vehicle>> vehicleJsonCodec = listJsonCodec(jsonCodec(Vehicle.class));

        Vehicle.validateVehicleListJsonCodec(vehicleJsonCodec);
    }

    @Test
    public void testTypeTokenList()
    {
        JsonCodec<List<Person>> jsonCodec = jsonCodec(new TypeToken<>() {});

        Person.validatePersonListJsonCodec(jsonCodec);

        JsonCodec<List<Vehicle>> vehicleJsonCodec = jsonCodec(new TypeToken<>() {});

        Vehicle.validateVehicleListJsonCodec(vehicleJsonCodec);
    }

    @Test
    public void testListNullValues()
    {
        JsonCodec<List<String>> jsonCodec = listJsonCodec(String.class);

        List<String> list = new ArrayList<>();
        list.add(null);
        list.add("abc");

        assertThat(jsonCodec.fromJson(jsonCodec.toJson(list))).isEqualTo(list);
    }

    @Test
    public void testMapJsonCodec()
    {
        JsonCodec<Map<String, Person>> jsonCodec = mapJsonCodec(String.class, Person.class);

        Person.validatePersonMapJsonCodec(jsonCodec);

        JsonCodec<Map<String, Vehicle>> vehicleJsonCodec = mapJsonCodec(String.class, Vehicle.class);

        Vehicle.validateVehicleMapJsonCodec(vehicleJsonCodec);
    }

    @Test
    public void testMapJsonCodecFromJsonCodec()
    {
        JsonCodec<Map<String, Person>> jsonCodec = mapJsonCodec(String.class, jsonCodec(Person.class));

        Person.validatePersonMapJsonCodec(jsonCodec);

        JsonCodec<Map<String, Vehicle>> vehicleJsonCodec = mapJsonCodec(String.class, jsonCodec(Vehicle.class));

        Vehicle.validateVehicleMapJsonCodec(vehicleJsonCodec);
    }

    @Test
    public void testTypeLiteralMap()
    {
        JsonCodec<Map<String, Person>> jsonCodec = jsonCodec(new TypeToken<>() {});

        Person.validatePersonMapJsonCodec(jsonCodec);

        JsonCodec<Map<String, Vehicle>> vehicleJsonCodec = jsonCodec(new TypeToken<>() {});

        Vehicle.validateVehicleMapJsonCodec(vehicleJsonCodec);
    }

    @Test
    public void testMapNullValues()
    {
        JsonCodec<Map<String, String>> jsonCodec = mapJsonCodec(String.class, String.class);

        Map<String, String> map = new HashMap<>();
        map.put("x", null);
        map.put("y", "abc");

        assertThat(jsonCodec.fromJson(jsonCodec.toJson(map))).isEqualTo(map);
    }

    @Test
    public void testImmutableJsonCodec()
    {
        JsonCodec<ImmutablePerson> jsonCodec = jsonCodec(ImmutablePerson.class);

        ImmutablePerson.validatePersonJsonCodec(jsonCodec);
    }

    @Test
    public void testAsymmetricJsonCodec()
    {
        JsonCodec<ImmutablePerson> jsonCodec = jsonCodec(ImmutablePerson.class);
        ImmutablePerson immutablePerson = jsonCodec.fromJson("{ \"notWritable\": \"foo\" }");
        assertThat(immutablePerson.getNotWritable()).isNull();
    }

    @Test
    public void testImmutableListJsonCodec()
    {
        JsonCodec<List<ImmutablePerson>> jsonCodec = listJsonCodec(ImmutablePerson.class);

        ImmutablePerson.validatePersonListJsonCodec(jsonCodec);
    }

    @Test
    public void testImmutableListJsonCodecFromJsonCodec()
    {
        JsonCodec<List<ImmutablePerson>> jsonCodec = listJsonCodec(jsonCodec(ImmutablePerson.class));

        ImmutablePerson.validatePersonListJsonCodec(jsonCodec);
    }

    @Test
    public void testImmutableTypeTokenList()
    {
        JsonCodec<List<ImmutablePerson>> jsonCodec = jsonCodec(new TypeToken<>() {});

        ImmutablePerson.validatePersonListJsonCodec(jsonCodec);
    }

    @Test
    public void testImmutableMapJsonCodec()
    {
        JsonCodec<Map<String, ImmutablePerson>> jsonCodec = mapJsonCodec(String.class, ImmutablePerson.class);

        ImmutablePerson.validatePersonMapJsonCodec(jsonCodec);
    }

    @Test
    public void testImmutableMapJsonCodecFromJsonCodec()
    {
        JsonCodec<Map<String, ImmutablePerson>> jsonCodec = mapJsonCodec(String.class, jsonCodec(ImmutablePerson.class));

        ImmutablePerson.validatePersonMapJsonCodec(jsonCodec);
    }

    @Test
    public void testImmutableTypeTokenMap()
    {
        JsonCodec<Map<String, ImmutablePerson>> jsonCodec = jsonCodec(new TypeToken<>() {});

        ImmutablePerson.validatePersonMapJsonCodec(jsonCodec);
    }

    @Test
    public void testToJsonWithLengthLimitSimple()
    {
        JsonCodec<ImmutablePerson> jsonCodec = jsonCodec(ImmutablePerson.class);
        ImmutablePerson person = new ImmutablePerson("a".repeat(1000), false);

        assertThat(jsonCodec.toJsonWithLengthLimit(person, 0)).isNotPresent();
        assertThat(jsonCodec.toJsonWithLengthLimit(person, 1000)).isNotPresent();
        assertThat(jsonCodec.toJsonWithLengthLimit(person, 1035)).isNotPresent();
        assertThat(jsonCodec.toJsonWithLengthLimit(person, 1036)).isPresent();
    }

    @Test
    public void testToJsonExceedingDefaultStringLimit()
    {
        JsonCodec<ImmutablePerson> jsonCodec = jsonCodec(ImmutablePerson.class);
        ImmutablePerson person = new ImmutablePerson("a".repeat(DEFAULT_MAX_STRING_LEN + 1), false);

        String json = jsonCodec.toJson(person);
        assertThat(jsonCodec.fromJson(json)).isEqualTo(person);

        byte[] bytes = jsonCodec.toJsonBytes(person);
        assertThat(jsonCodec.fromJson(bytes)).isEqualTo(person);

        assertThat(jsonCodec.fromJson(new ByteArrayInputStream(bytes))).isEqualTo(person);

        assertThat(jsonCodec.fromJson(new InputStreamReader(new ByteArrayInputStream(bytes), UTF_8))).isEqualTo(person);
    }

    @Test
    public void testToJsonWithLengthLimitNonAscii()
    {
        JsonCodec<ImmutablePerson> jsonCodec = jsonCodec(ImmutablePerson.class);
        ImmutablePerson person = new ImmutablePerson("\u0158".repeat(1000), false);

        assertThat(jsonCodec.toJsonWithLengthLimit(person, 0)).isNotPresent();
        assertThat(jsonCodec.toJsonWithLengthLimit(person, 1000)).isNotPresent();
        assertThat(jsonCodec.toJsonWithLengthLimit(person, 1035)).isNotPresent();
        assertThat(jsonCodec.toJsonWithLengthLimit(person, 1036)).isPresent();
    }

    @Test
    public void testToJsonWithLengthLimitComplex()
    {
        JsonCodec<List<ImmutablePerson>> jsonCodec = listJsonCodec(jsonCodec(ImmutablePerson.class));
        ImmutablePerson person = new ImmutablePerson("a".repeat(1000), false);
        List<ImmutablePerson> people = Collections.nCopies(10, person);

        assertThat(jsonCodec.toJsonWithLengthLimit(people, 0)).isNotPresent();
        assertThat(jsonCodec.toJsonWithLengthLimit(people, 5000)).isNotPresent();
        assertThat(jsonCodec.toJsonWithLengthLimit(people, 10381)).isNotPresent();
        assertThat(jsonCodec.toJsonWithLengthLimit(people, 10382)).isPresent();
    }

    @Test
    public void testToJsonWithLengthLimitWrappedBySerializer()
    {
        JsonCodec<WrappingSerializerValue> jsonCodec = jsonCodec(WrappingSerializerValue.class);

        assertThat(jsonCodec.toJsonWithLengthLimit(new WrappingSerializerValue("a".repeat(1000)), 100)).isNotPresent();
        assertThat(jsonCodec.toJsonWithLengthLimit(new WrappingSerializerValue("a".repeat(10)), 100)).isPresent();
    }

    @JsonSerialize(using = WrappingSerializer.class)
    public record WrappingSerializerValue(String value) {}

    public static class WrappingSerializer
            extends ValueSerializer<WrappingSerializerValue>
    {
        @Override
        public void serialize(WrappingSerializerValue value, JsonGenerator generator, SerializationContext context)
        {
            try {
                generator.writeString(value.value());
                generator.flush();
            }
            catch (JacksonException e) {
                throw DatabindException.from(generator, "wrapped", e);
            }
        }
    }

    @Test
    public void testToJsonWithLengthLimitFailure()
    {
        JsonCodec<FailingGetter> jsonCodec = jsonCodec(FailingGetter.class);

        assertThatThrownBy(() -> jsonCodec.toJsonWithLengthLimit(new FailingGetter(), 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("%s could not be converted to JSON".formatted(FailingGetter.class.getName()));
    }

    public static class FailingGetter
    {
        @JsonProperty
        public String getValue()
        {
            throw new IllegalStateException("getter failed");
        }
    }

    @Test
    public void testSingleComponentRecord()
    {
        JsonCodec<MyRecord> jsonCodec = jsonCodec(MyRecord.class);

        assertThat(jsonCodec.toJson(new MyRecord("value"))).isEqualTo("{\n  \"foo\" : \"value\"\n}");
        assertThat(jsonCodec.fromJson("{\"foo\":\"value\"}")).isEqualTo(new MyRecord("value"));
        assertThatThrownBy(() -> jsonCodec.fromJson("\"value\""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testSingleComponentRecordWithOverriddenIntrospection()
    {
        // records using legacy introspection or explicit @JsonAutoDetect are still not read from a bare scalar
        JsonCodec<LegacySingleComponentRecord> legacyCodec = jsonCodec(LegacySingleComponentRecord.class);
        assertThat(legacyCodec.fromJson("{\"value\":\"abc\"}")).isEqualTo(new LegacySingleComponentRecord("abc"));
        assertThatThrownBy(() -> legacyCodec.fromJson("\"abc\""))
                .isInstanceOf(IllegalArgumentException.class);

        JsonCodec<AutoDetectSingleComponentRecord> autoDetectCodec = jsonCodec(AutoDetectSingleComponentRecord.class);
        assertThat(autoDetectCodec.fromJson("{\"value\":\"abc\"}")).isEqualTo(new AutoDetectSingleComponentRecord("abc"));
        assertThatThrownBy(() -> autoDetectCodec.fromJson("\"abc\""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings("removal")
    @RecordAutoDetectModule.LegacyRecordIntrospection
    public record LegacySingleComponentRecord(String value) {}

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public record AutoDetectSingleComponentRecord(String value) {}

    @Test
    public void testUpperCaseAccessorNaming()
    {
        // Jackson 3 uses standard bean naming: getURL() maps to "URL" (Jackson 2 used "url"); name the property to keep "url"
        assertThat(mapJsonCodec(String.class, String.class).fromJson(jsonCodec(UpperCaseGetters.class).toJson(new UpperCaseGetters())))
                .containsExactlyInAnyOrderEntriesOf(Map.of("URL", "implicit", "url", "explicit"));
    }

    public static class UpperCaseGetters
    {
        @JsonProperty
        public String getURL()
        {
            return "implicit";
        }

        @JsonProperty("url")
        public String getExplicitURL()
        {
            return "explicit";
        }
    }

    @Test
    public void testMonthFromNumberIsZeroBased()
    {
        assertThat(jsonCodec(Month.class).fromJson("0")).isEqualTo(Month.JANUARY);
        assertThat(jsonCodec(Month.class).fromJson("11")).isEqualTo(Month.DECEMBER);
        assertThat(jsonCodec(Month.class).toJson(Month.MARCH)).isEqualTo("\"MARCH\"");
    }

    @Test
    public void testMonthAndYearHonorJsonFormat()
    {
        assertThat(jsonCodec(FormattedMonthAndYear.class).toJson(new FormattedMonthAndYear(Month.MARCH, Month.MARCH, Year.of(2024), Year.of(2024), Year.of(2024))))
                .isEqualTo(
                        """
                        {
                          "plainMonth" : "MARCH",
                          "numberMonth" : 2,
                          "plainYear" : "2024",
                          "patternYear" : "24",
                          "numberYear" : 2024
                        }\
                        """);
    }

    public record FormattedMonthAndYear(
            Month plainMonth,
            @JsonFormat(shape = JsonFormat.Shape.NUMBER) Month numberMonth,
            Year plainYear,
            @JsonFormat(pattern = "uu") Year patternYear,
            @JsonFormat(shape = JsonFormat.Shape.NUMBER) Year numberYear) {}

    @Test
    public void testSupplementaryCharactersAreEscapedInBytes()
    {
        // Jackson 2.x wrote characters outside the BMP as escaped surrogate pairs
        assertThat(new String(jsonCodec(String.class).toJsonBytes("\uD83D\uDE00"), UTF_8)).isEqualTo("\"\\uD83D\\uDE00\"");
        assertThat(jsonCodec(String.class).fromJson(jsonCodec(String.class).toJsonBytes("\uD83D\uDE00"))).isEqualTo("\uD83D\uDE00");
    }

    @Test
    public void testJavaTimeAndDateWireFormat()
    {
        assertThat(jsonCodec(Year.class).toJson(Year.of(2024))).isEqualTo("\"2024\"");
        assertThat(jsonCodec(Year.class).fromJson("\"2024\"")).isEqualTo(Year.of(2024));
        assertThat(jsonCodec(Date.class).toJson(new Date(0))).isEqualTo("\"1970-01-01T00:00:00.000+00:00\"");
        assertThat(jsonCodec(Instant.class).toJson(Instant.EPOCH)).isEqualTo("\"1970-01-01T00:00:00Z\"");
    }

    @Test
    public void testTrailingContent()
    {
        JsonCodec<ImmutablePerson> codec = jsonCodec(ImmutablePerson.class);

        String json = "{\"name\":\"Me\",\"rocks\":true}";
        assertThat(codec.fromJson(json).getName()).isEqualTo("Me");

        String jsonWithTrailingContent = json + " trailer";
        assertThatThrownBy(() -> codec.fromJson(jsonWithTrailingContent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid JSON string for [simple type, class io.airlift.json.ImmutablePerson]")
                .hasStackTraceContaining("Unrecognized token 'trailer': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')");

        assertThatThrownBy(() -> codec.fromJson(jsonWithTrailingContent.getBytes(UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid JSON bytes for [simple type, class io.airlift.json.ImmutablePerson]")
                .hasStackTraceContaining("Unrecognized token 'trailer': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')");

        assertThatThrownBy(() -> codec.fromJson(new ByteArrayInputStream(jsonWithTrailingContent.getBytes(UTF_8))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid JSON stream for [simple type, class io.airlift.json.ImmutablePerson]")
                .hasStackTraceContaining("Unrecognized token 'trailer': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')");

        assertThatThrownBy(() -> codec.fromJson(Reader.of(jsonWithTrailingContent)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid JSON characters for [simple type, class io.airlift.json.ImmutablePerson]")
                .hasStackTraceContaining("Unrecognized token 'trailer': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or 'false')");

        String jsonWithTrailingJsonContent = json + " \"valid json value\"";
        assertThatThrownBy(() -> codec.fromJson(jsonWithTrailingJsonContent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Found characters after the expected end of input");

        assertThatThrownBy(() -> codec.fromJson(jsonWithTrailingJsonContent.getBytes(UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Found characters after the expected end of input");

        assertThatThrownBy(() -> codec.fromJson(new ByteArrayInputStream(jsonWithTrailingJsonContent.getBytes(UTF_8))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Found characters after the expected end of input");

        assertThatThrownBy(() -> codec.fromJson(Reader.of(jsonWithTrailingJsonContent)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Found characters after the expected end of input");
    }

    @Test
    public void testRecordSerialization()
    {
        assertSerializationRoundTrip(
                jsonCodec(MyRecord.class),
                new MyRecord("my value"),
                """
                {
                  "foo" : "my value"
                }\
                """);

        assertSerializationRoundTrip(
                JsonCodec.jsonCodec(MyRecordWithBeanLikeGetter.class),
                new MyRecordWithBeanLikeGetter("my value"),
                """
                {
                  "foo" : "my value"
                }\
                """);

        assertSerializationRoundTrip(
                JsonCodec.jsonCodec(MyRecordAdditionalGetter.class),
                new MyRecordAdditionalGetter("my value", true, true),
                """
                {
                  "foo" : "my value",
                  "condition" : true,
                  "isCool" : true,
                  "additionalProperty" : "additional property value"
                }\
                """);

        assertThat(JsonCodec.jsonCodec(LegacyRecordAdditionalGetter.class).toJson(new LegacyRecordAdditionalGetter("my value"))).isEqualTo(
                """
                {
                  "foo" : "not really a foo value",
                  "bar" : "there is no bar field in the record",
                  "safe" : false
                }\
                """);
    }

    private static <T> void assertSerializationRoundTrip(JsonCodec<T> codec, T object, String expected)
    {
        assertThat(codec.toJson(object)).isEqualTo(expected);
        assertThat(codec.fromJson(expected)).isEqualTo(object);
    }

    public record MyRecord(String foo) {}

    public record MyRecordWithBeanLikeGetter(String foo)
    {
        public String getFoo()
        {
            return foo();
        }
    }

    @JsonPropertyOrder(alphabetic = true)
    public record MyRecordAdditionalGetter(
            // a basic property
            String foo,
            // a boolean property that has additional getter and is-getter
            boolean condition,
            // a boolean property named as an is-getter
            boolean isCool)
    {
        // Might shadow actual getter for foo -- the foo() method
        public String getFoo()
        {
            throw new UnsupportedOperationException("this method should not be called during serialization");
        }

        // Looks like a getter for "bar" property, there is no such record component
        public String getBar()
        {
            return "there is no bar field in the record";
        }

        // Not a record component, but explicitly requested to be included in serialization
        @JsonProperty
        public String getAdditionalProperty()
        {
            return "additional property value";
        }

        // Looks like record-style getter for "baz" property, there is no such record component
        public String baz()
        {
            throw new UnsupportedOperationException("this method should not be called during serialization");
        }

        // Might shadow actual getter for condition -- the condition() method
        public boolean isCondition()
        {
            throw new UnsupportedOperationException("this method should not be called during serialization");
        }

        // Might shadow actual getter for condition -- the condition() method
        public boolean getCondition()
        {
            throw new UnsupportedOperationException("this method should not be called during serialization");
        }
    }

    @SuppressWarnings("removal")
    @JsonPropertyOrder(alphabetic = true)
    @RecordAutoDetectModule.LegacyRecordIntrospection
    public record LegacyRecordAdditionalGetter(String foo)
    {
        // Not the canonical accessor for the foo record component, but takes precedence when serializing to JSON
        public String getFoo()
        {
            return "not really a foo value";
        }

        // Not a record component, but gets serialized to JSON
        public String getBar()
        {
            return "there is no bar field in the record";
        }

        // Not a record component, but gets serialized to JSON
        public boolean isSafe()
        {
            return false;
        }
    }
}
