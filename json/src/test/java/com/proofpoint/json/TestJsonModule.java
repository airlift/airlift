package com.proofpoint.json;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.ObjectMapper;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static org.joda.time.DateTimeZone.UTC;
import static org.testng.Assert.assertEquals;

public class TestJsonModule
{
    public static final Car CAR = new Car().setMake("BMW").setModel("M3").setYear(2011).setPurchased(new DateTime().withZone(UTC)).setNotes("sweet!");
    private ObjectMapper objectMapper;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        Injector injector = Guice.createInjector(new JsonModule());
        objectMapper = injector.getInstance(ObjectMapper.class);
    }

    @Test
    public void testSetup()
            throws Exception
    {
        assertEquals(CAR, CAR);
        assertEquals(objectMapper.readValue(objectMapper.writeValueAsString(CAR), Car.class), CAR);
        assertEquals(objectMapper.readValue(objectMapper.writeValueAsString(CAR), Car.class), CAR);
    }

    @Test
    public void testFieldDetection()
            throws Exception
    {
        Map<String, Object> actual = objectMapper.readValue(objectMapper.writeValueAsString(CAR), Map.class);

        // notes is not annotated so should not be included
        // color is null so should not be included
        assertEquals(actual.keySet(), ImmutableSet.of("make", "model", "year", "purchased"));
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
            return result;
        }

        @Override
        public String toString()
        {
            final StringBuffer sb = new StringBuffer();
            sb.append("Car");
            sb.append("{make='").append(make).append('\'');
            sb.append(", model='").append(model).append('\'');
            sb.append(", year=").append(year);
            sb.append(", purchased=").append(purchased);
            sb.append(", color='").append(color).append('\'');
            sb.append(", notes='").append(notes).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }
}
