/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.airlift.event.client;

import org.testng.annotations.Test;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static io.airlift.event.client.TypeParameterUtils.getTypeParameters;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

@SuppressWarnings("UnusedDeclaration")
public class TestTypeParametersUtils
{
    @SuppressWarnings("RawUseOfParameterizedType")
    public Map mapNotGeneric;
    public Map<Key, Value> mapSimple;
    public Map<?, ?> mapWildcard;
    public Map<? extends Key, ? extends Value> mapExtendsWildcard;
    public Map<? super Key, ? extends Value> mapSuperWildcard;

    @SuppressWarnings("RawUseOfParameterizedType")
    public MyMap myMapNotGeneric;
    public MyMap<Fake, Value, Key> myMapSimple;
    public MyMap<?, ?, ?> myMapWildcard;
    public MyMap<? extends Fake, ? extends Value, ? extends Key> myMapExtendsWildcard;
    public MyMap<? super Fake, ? super Value, ? extends Key> myMapSuperWildcard;

    public FixedMap fixedMap;

    @Test
    public void testMap()
            throws Exception
    {
        assertNull(getParameters("mapNotGeneric"));
        assertEquals(getParameters("mapSimple"), new Type[] { Key.class, Value.class });
        assertTwoWildcardTypes(getParameters("mapWildcard"));
        assertTwoWildcardTypes(getParameters("mapExtendsWildcard"));
        assertTwoWildcardTypes(getParameters("mapSuperWildcard"));
    }

    @Test
    public void testMyMap()
            throws Exception
    {
        assertTwoTypeVariables(getParameters("myMapNotGeneric"));
        assertEquals(getParameters("myMapSimple"), new Type[] { Key.class, Value.class });
        assertTwoWildcardTypes(getParameters("myMapWildcard"));
        assertTwoWildcardTypes(getParameters("myMapExtendsWildcard"));
        assertTwoWildcardTypes(getParameters("myMapSuperWildcard"));
    }

    @Test
    public void testFixedMap()
            throws Exception
    {
        assertEquals(getParameters("fixedMap"), new Type[] { Key.class, Value.class });
    }

    private static void assertTwoWildcardTypes(Type[] types)
    {
        assertEquals(types.length, 2);
        for (Type type : types) {
            assertInstanceOf(type, WildcardType.class);
        }
    }

    private static void assertTwoTypeVariables(Type[] types)
    {
        assertEquals(types.length, 2);
        for (Type type : types) {
            assertInstanceOf(type, TypeVariable.class);
        }
    }

    private static void assertInstanceOf(Type type, Class<?> clazz)
    {
        assertTrue(clazz.isInstance(type), String.format("[%s] is not instance of %s", type, clazz.getSimpleName()));
    }

    private Type[] getParameters(String fieldName)
            throws Exception
    {
        return getTypeParameters(Map.class, getClass().getField(fieldName).getGenericType());
    }

    public static class MyMap<Unused, MapValue, MapKey>
            extends AbstractMap<MapKey, MapValue>
    {
        public Iterator<MapKey> iterator()
        {
            return null;
        }

        public int size()
        {
            return 0;
        }

        public Set<Entry<MapKey, MapValue>> entrySet()
        {
            return null;
        }
    }

    public static class FixedMap
            extends MyMap<Fake, Value, Key>
    {}

    public static class Fake
    {}

    public static class Key
    {}

    public static class Value
    {}
}
