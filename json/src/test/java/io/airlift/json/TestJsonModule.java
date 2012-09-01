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

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.DeserializationContext;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.deser.StdScalarDeserializer;
import org.codehaus.jackson.map.ser.ToStringSerializer;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static io.airlift.json.JsonBinder.jsonBinder;
import static org.joda.time.DateTimeZone.UTC;
import static org.testng.Assert.assertEquals;

public class TestJsonModule
{
    public static final Car CAR = new Car()
            .setMake("BMW")
            .setModel("M3")
            .setYear(2011)
            .setPurchased(new DateTime().withZone(UTC))
            .setNotes("sweet!")
            .setNameList(superDuper("d*a*i*n"));

    private ObjectMapper objectMapper;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        Injector injector = Guice.createInjector(new JsonModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        jsonBinder(binder).addSerializerBinding(SuperDuperNameList.class).toInstance(ToStringSerializer.instance);
                        jsonBinder(binder).addDeserializerBinding(SuperDuperNameList.class).to(SuperDuperNameListDeserializer.class);
                    }
                });
        objectMapper = injector.getInstance(ObjectMapper.class);
    }

    @Test
    public void testJsonCodecFactoryBinding()
            throws Exception
    {
        Injector injector = Guice.createInjector(new JsonModule());
        JsonCodecFactory codecFactory = injector.getInstance(JsonCodecFactory.class);

        Person.validatePersonJsonCodec(codecFactory.jsonCodec(Person.class));
        Person.validatePersonListJsonCodec(codecFactory.listJsonCodec(Person.class));
        Person.validatePersonMapJsonCodec(codecFactory.mapJsonCodec(String.class, Person.class));
    }

    @Test
    public void testSetup()
            throws Exception
    {
        assertEquals(CAR, CAR);
        String json = objectMapper.writeValueAsString(CAR);
        Car actual = objectMapper.readValue(json, Car.class);
        assertEquals(actual, CAR);
    }

    @Test
    public void testFieldDetection()
            throws Exception
    {
        Map<String, Object> actual = objectMapper.readValue(objectMapper.writeValueAsString(CAR), Map.class);

        // notes is not annotated so should not be included
        // color is null so should not be included
        assertEquals(actual.keySet(), ImmutableSet.of("make", "model", "year", "purchased", "nameList"));
    }

    @Test
    public void testDateTimeRendered()
            throws Exception
    {
        Map<String, Object> actual = objectMapper.readValue(objectMapper.writeValueAsString(CAR), Map.class);

        assertEquals(actual.get("purchased"), ISODateTimeFormat.dateTime().print(CAR.getPurchased()));
    }

    @Test
    public void testIgnoreUnknownFields()
            throws Exception
    {
        Map<String, Object> data = Maps.newHashMap(objectMapper.readValue(objectMapper.writeValueAsString(CAR), Map.class));

        // add an unknown field
        data.put("unknown", "bogus");

        // Jackson should deserialize the object correctly with the extra unknown data
        assertEquals(objectMapper.readValue(objectMapper.writeValueAsString(data), Car.class), CAR);
    }

    public static class Car
    {
        // These fields are public to make sure that Jackson is ignoring them
        public String make;
        public String model;
        public int year;
        public DateTime purchased;

        // property that will be null to verify that null fields are not rendered
        public String color;

        // non-json property to verify that auto-detection is disabled
        public String notes;

        // property that requires special serializer and deserializer
        public SuperDuperNameList nameList;

        @JsonProperty
        public String getMake()
        {
            return make;
        }

        @JsonProperty
        public Car setMake(String make)
        {
            this.make = make;
            return this;
        }

        @JsonProperty
        public String getModel()
        {
            return model;
        }

        @JsonProperty
        public Car setModel(String model)
        {
            this.model = model;
            return this;
        }

        @JsonProperty
        public int getYear()
        {
            return year;
        }

        @JsonProperty
        public Car setYear(int year)
        {
            this.year = year;
            return this;
        }

        @JsonProperty
        public DateTime getPurchased()
        {
            return purchased;
        }

        @JsonProperty
        public Car setPurchased(DateTime purchased)
        {
            this.purchased = purchased;
            return this;
        }

        @JsonProperty
        public String getColor()
        {
            return color;
        }

        @JsonProperty
        public Car setColor(String color)
        {
            this.color = color;
            return this;
        }

        @JsonProperty
        public SuperDuperNameList getNameList()
        {
            return nameList;
        }

        @JsonProperty
        public Car setNameList(SuperDuperNameList nameList)
        {
            this.nameList = nameList;
            return this;
        }

        // this field should not be written

        public String getNotes()
        {
            return notes;
        }

        public Car setNotes(String notes)
        {
            this.notes = notes;
            return this;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Car)) {
                return false;
            }

            Car car = (Car) o;

            if (year != car.year) {
                return false;
            }
            if (color != null ? !color.equals(car.color) : car.color != null) {
                return false;
            }
            if (make != null ? !make.equals(car.make) : car.make != null) {
                return false;
            }
            if (model != null ? !model.equals(car.model) : car.model != null) {
                return false;
            }
            if (nameList != null ? !nameList.equals(car.nameList) : car.nameList != null) {
                return false;
            }
            if (purchased != null ? !purchased.equals(car.purchased) : car.purchased != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = make != null ? make.hashCode() : 0;
            result = 31 * result + (model != null ? model.hashCode() : 0);
            result = 31 * result + year;
            result = 31 * result + (purchased != null ? purchased.hashCode() : 0);
            result = 31 * result + (color != null ? color.hashCode() : 0);
            result = 31 * result + (nameList != null ? nameList.hashCode() : 0);
            return result;
        }

        @Override
        public String toString()
        {
            return Objects.toStringHelper(this)
                    .add("make", make)
                    .add("model", model)
                    .add("year", year)
                    .add("purchased", purchased)
                    .add("color", color)
                    .add("notes", notes)
                    .add("nameList", nameList)
                    .toString();
        }
    }

    public static class SuperDuperNameList
    {
        private List<String> name;

        private SuperDuperNameList(String superDuperNameList)
        {
            this(superDuperNameList, null);
        }

        private SuperDuperNameList(String superDuperNameList, Object stopJacksonFromUsingStringConstructor)
        {
            this.name = ImmutableList.copyOf(Splitter.on('*').split(superDuperNameList));
        }

        public List<String> getName()
        {
            return name;
        }

        @Override
        public String toString()
        {
            return Joiner.on("*").join(name);
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SuperDuperNameList)) {
                return false;
            }

            SuperDuperNameList that = (SuperDuperNameList) o;

            if (!name.equals(that.name)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode()
        {
            return name.hashCode();
        }
    }

    public final static class SuperDuperNameListDeserializer
            extends StdScalarDeserializer<SuperDuperNameList>
    {
        public SuperDuperNameListDeserializer()
        {
            super(SuperDuperNameList.class);
        }

        @Override
        public SuperDuperNameList deserialize(JsonParser jp, DeserializationContext context)
                throws IOException
        {
            JsonToken token = jp.getCurrentToken();
            if (token == JsonToken.VALUE_STRING) {
                return new SuperDuperNameList(jp.getText(), null);
            }
            throw context.mappingException(getValueClass());
        }
    }

    public static SuperDuperNameList superDuper(String superDuperNameList)
    {
        return new SuperDuperNameList(superDuperNameList, null);
    }
}
