package com.proofpoint.reporting;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.management.AttributeNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import java.lang.reflect.Method;
import java.util.List;

import static com.proofpoint.reporting.Util.getMethod;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public abstract class AbstractReportedBeanTest<T>
{
    protected List<T> objects;
    protected final TestingBucketIdProvider bucketIdProvider = new TestingBucketIdProvider();

    protected abstract Object getObject(T t);

    protected abstract MBeanInfo getMBeanInfo(T t)
            throws Exception;

    protected abstract Object getAttribute(T t, String attributeName)
            throws Exception;

    @Test(dataProvider = "fixtures")
    public void testGetterAttributeInfo(String attribute, boolean isIs, Object[] values, Class<?> clazz)
            throws Exception
    {
        String methodName = "set" + attribute.replace(".", "");
        for (T t : objects) {
            String attributeName = toFeatureName(attribute, t);
            SimpleInterface simpleInterface = toSimpleInterface(t);
            Method setter = getMethod(simpleInterface.getClass(), methodName, clazz);

            MBeanInfo info = getMBeanInfo(t);
            MBeanAttributeInfo attributeInfo = getAttributeInfo(info, attributeName);
            assertNotNull(attributeInfo, "AttributeInfo for " + attributeName);
            assertEquals(attributeInfo.getName(), attributeName, "Attribute Name for " + attributeName);
            assertEquals(attributeInfo.getType(), setter.getParameterTypes()[0].getName(), "Attribute type for " + attributeName);
            assertEquals(attributeInfo.isIs(), isIs, "Attribute isIs for " + attributeName);
            assertTrue(attributeInfo.isReadable(), "Attribute Readable for " + attributeName);
            assertFalse(attributeInfo.isWritable(), "Attribute Writable for " + attributeName);
        }
    }

    @Test
    public void testNotReportedAttributeInfo()
            throws Exception
    {

        for (T t : objects) {
            MBeanInfo info = getMBeanInfo(t);
            String attributeName = toFeatureName("NotReported", t);
            MBeanAttributeInfo attributeInfo = getAttributeInfo(info, attributeName);
            assertNull(attributeInfo, "AttributeInfo for " + attributeName);
        }
    }

    protected MBeanAttributeInfo getAttributeInfo(MBeanInfo info, String attributeName)
    {
        for (MBeanAttributeInfo attributeInfo : info.getAttributes()) {
            if (attributeInfo.getName().equals(attributeName)) {
                return attributeInfo;
            }
        }
        return null;
    }

    @Test(dataProvider = "fixtures")
    public void testGet(String attribute, boolean isIs, Object[] values, Class<?> clazz)
            throws Exception
    {
        String methodName = "set" + attribute.replace(".", "");
        for (T t : objects) {
            String attributeName = toFeatureName(attribute, t);
            SimpleInterface simpleInterface = toSimpleInterface(t);
            Method setter = getMethod(simpleInterface.getClass(), methodName, clazz);

            for (Object value : values) {
                setter.invoke(simpleInterface, value);
                bucketIdProvider.advance();

                if (isIs && value != null) {
                    if ((Boolean) value) {
                        value = 1;
                    }
                    else {
                        value = 0;
                    }
                }

                assertEquals(getAttribute(t, attributeName), value);
            }
        }
    }

    @Test
    public void testGetFailsOnNotReported()
            throws Exception
    {

        for (T t : objects) {
            try {
                getAttribute(t, "NotReported");
                fail("Should not allow getting unreported attribute");
            }
            catch (AttributeNotFoundException e) {
                // ignore
            }
        }
    }

    @DataProvider(name = "fixtures")
    Object[][] getFixtures()
    {
        return new Object[][] {

                new Object[] { "BooleanValue", true, new Object[] { true, false }, Boolean.TYPE },
                new Object[] { "BooleanBoxedValue", true, new Object[] { true, false, null }, Boolean.class },
                new Object[] { "ByteValue", false, new Object[] { Byte.MAX_VALUE, Byte.MIN_VALUE, (byte) 0 },
                               Byte.TYPE },
                new Object[] { "ByteBoxedValue", false, new Object[] { Byte.MAX_VALUE, Byte.MIN_VALUE, (byte) 0, null },
                               Byte.class },

                new Object[] { "ShortValue", false, new Object[] { Short.MAX_VALUE, Short.MIN_VALUE, (short) 0 },
                               Short.TYPE },
                new Object[] { "ShortBoxedValue", false,
                               new Object[] { Short.MAX_VALUE, Short.MIN_VALUE, (short) 0, null }, Short.class },

                new Object[] { "IntegerValue", false, new Object[] { Integer.MAX_VALUE, Integer.MIN_VALUE, 0 },
                               Integer.TYPE },
                new Object[] { "IntegerBoxedValue", false,
                               new Object[] { Integer.MAX_VALUE, Integer.MIN_VALUE, 0, null }, Integer.class },

                new Object[] { "LongValue", false, new Object[] { Long.MAX_VALUE, Long.MIN_VALUE, 0L }, Long.TYPE },
                new Object[] { "LongBoxedValue", false, new Object[] { Long.MAX_VALUE, Long.MIN_VALUE, 0L, null },
                               Long.class },

                new Object[] { "FloatValue", false,
                               new Object[] { -Float.MIN_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, 0.0f,
                                              Float.NaN }, Float.TYPE },
                new Object[] { "FloatBoxedValue", false,
                               new Object[] { -Float.MIN_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, 0.0f,
                                              Float.NaN, null }, Float.class },

                new Object[] { "DoubleValue", false,
                               new Object[] { -Double.MIN_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, Double.MIN_VALUE,
                                              0.0, Double.NaN }, Double.TYPE },
                new Object[] { "DoubleBoxedValue", false,
                               new Object[] { -Double.MIN_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, Double.MIN_VALUE,
                                              0.0, Double.NaN }, Double.class },

                new Object[] { "StringValue", false, new Object[] { null, "hello there" }, String.class },

                new Object[] { "ObjectValue", false, new Object[] { "random object", 1, true }, Object.class },

                new Object[] { "PrivateValue", false, new Object[] { Integer.MAX_VALUE, Integer.MIN_VALUE, 0 },
                        Integer.TYPE },

                new Object[] { "BucketedBooleanValue", true, new Object[] { true, false }, Boolean.TYPE },
                new Object[] { "BucketedIntegerValue", false, new Object[] { Integer.MAX_VALUE, Integer.MIN_VALUE, 0 },
                               Integer.TYPE },
                new Object[] { "NestedBucket.BucketedBooleanBoxedValue", true, new Object[] { true, false, null }, Boolean.class },
                new Object[] { "NestedBucket.BucketedLongValue", false, new Object[] { Long.MAX_VALUE, Long.MIN_VALUE, 0L }, Long.TYPE },
                new Object[] { "BucketedBooleanBoxedValue", true, new Object[] { true, false, null }, Boolean.class },
                new Object[] { "BucketedLongValue", false, new Object[] { Long.MAX_VALUE, Long.MIN_VALUE, 0L }, Long.TYPE },
        };
    }

    protected String toFeatureName(String attribute, T t)
    {
        String attributeName;
        if (getObject(t) instanceof NestedObject) {
            attributeName = "SimpleObject." + attribute;
        }
        else {
            attributeName = attribute;
        }
        return attributeName;
    }

    protected SimpleInterface toSimpleInterface(T t)
    {
        SimpleInterface simpleInterface;
        if (getObject(t) instanceof SimpleInterface) {
            simpleInterface = (SimpleInterface) getObject(t);
        }
        else if (getObject(t) instanceof FlattenObject) {
            simpleInterface = ((FlattenObject) getObject(t)).getSimpleObject();
        }
        else if (getObject(t) instanceof NestedObject) {
            simpleInterface = ((NestedObject) getObject(t)).getSimpleObject();
        }
        else {
            throw new IllegalArgumentException("Expected objects implementing SimpleInterface or FlattenObject but got " + getObject(t).getClass().getName());
        }
        return simpleInterface;
    }

    private static class TestingBucketIdProvider
            implements BucketIdProvider
    {
        private int bucketId = 0;

        @Override
        public int get()
        {
            return bucketId;
        }

        public void advance()
        {
            ++bucketId;
        }
    }
}
