/*
 *  Copyright 2009 Martin Traverso
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.proofpoint.reporting;

import java.lang.reflect.Method;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

public abstract class TestInheritanceBase
{
    protected final Class<?> target;
    protected final Class<?> source;

    /**
     * @param target class to resolve
     * @param source class providing the annotation
     */
    TestInheritanceBase(Class<?> target, Class<?> source)
    {
        this.target = target;
        this.source = source;
    }

    public Class<?> getTargetClass()
    {
        return target;
    }

    public Method getTargetMethod() throws NoSuchMethodException
    {
        return target.getMethod("getValue");
    }

    public Method expected() throws NoSuchMethodException
    {
        return source.getDeclaredMethod("getValue");
    }

    @Test
    public void testResolver() throws NoSuchMethodException
    {
        Map<Method, Method> map = AnnotationUtils.findReportedMethods(getTargetClass());
        Method annotatedMethod = map.get(getTargetMethod());
        Assert.assertEquals(annotatedMethod, expected());
    }
}
