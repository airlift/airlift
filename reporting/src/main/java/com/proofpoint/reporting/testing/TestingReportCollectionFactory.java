/*
 * Copyright 2014 Proofpoint, Inc.
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
package com.proofpoint.reporting.testing;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Ticker;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.MutableClassToInstanceMap;
import com.proofpoint.reporting.ReportCollectionFactory;
import com.proofpoint.reporting.ReportExporter;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Optional.fromNullable;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.reflect.Proxy.newProxyInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class TestingReportCollectionFactory
    extends ReportCollectionFactory
{
    private final NamedInstanceMap argumentVerifierMap = new NamedInstanceMap();
    private final NamedInstanceMap superMap = new NamedInstanceMap();

    public TestingReportCollectionFactory()
    {
        super(mock(ReportExporter.class), new Ticker()
        {
            @Override
            public long read()
            {
                return 0;
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T createReportCollection(Class<T> aClass)
    {
        checkNotNull(aClass, "class is null");

        T argumentVerifier = mock(aClass);
        argumentVerifierMap.put(null, aClass, argumentVerifier);

        T superCollection = super.createReportCollection(aClass);
        superMap.put(null, aClass, superCollection);

        return (T) newProxyInstance(
                aClass.getClassLoader(),
                new Class[]{aClass},
                new TestingInvocationHandler(argumentVerifier, superCollection));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T createReportCollection(Class<T> aClass, String name)
    {
        checkNotNull(aClass, "class is null");
        checkNotNull(name, "name is null");

        T argumentVerifier = mock(aClass);
        argumentVerifierMap.put(name, aClass, argumentVerifier);

        T superCollection = super.createReportCollection(aClass);
        superMap.put(name, aClass, superCollection);

        return (T) newProxyInstance(
                aClass.getClassLoader(),
                new Class[]{aClass},
                new TestingInvocationHandler(argumentVerifier, superCollection));
    }

    public <T> T getArgumentVerifier(Class<T> aClass)
    {
        return argumentVerifierMap.get(null, aClass);
    }

    public <T> T getArgumentVerifier(Class<T> aClass, String name)
    {
        checkNotNull(name, "name is null");
        return argumentVerifierMap.get(name, aClass);
    }

    public <T> T getReportCollection(Class<T> aClass)
    {
        return superMap.get(null, aClass);
    }

    public <T> T getReportCollection(Class<T> aClass, String name)
    {
        checkNotNull(name, "name is null");
        return superMap.get(name, aClass);
    }

    @Override
    protected Supplier<Object> getReturnValueSupplier(Method method)
    {
        final Supplier<Object> superSupplier = super.getReturnValueSupplier(method);
        return new Supplier<Object>()
        {
            @Override
            public Object get()
            {
                return spy(superSupplier.get());
            }
        };
    }

    private static class TestingInvocationHandler<T>
            implements InvocationHandler
    {
        private final T argumentVerifier;
        private final T superCollection;

        public TestingInvocationHandler(T argumentVerifier, T superCollection)
        {
            this.argumentVerifier = argumentVerifier;
            this.superCollection = superCollection;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable
        {
            method.invoke(argumentVerifier, args);
            return method.invoke(superCollection, args);
        }
    }

    private static class NamedInstanceMap
    {
        private final Map<Optional<String>, ClassToInstanceMap<Object>> nameMap = new HashMap<>();

        public synchronized <T> void put(@Nullable String name, Class<T> aClass, T value)
        {
            ClassToInstanceMap<Object> instanceMap = nameMap.get(fromNullable(name));
            if (instanceMap == null) {
                instanceMap = MutableClassToInstanceMap.create();
                nameMap.put(fromNullable(name), instanceMap);
            }
            if (instanceMap.putInstance(aClass, value) != null) {
                String message = "Duplicate ReportCollection for " + aClass.toString();
                if (name != null) {
                    message += " named " + name;
                }
                throw new Error(message);
            }
        }

        @SuppressWarnings("unchecked")
        public synchronized <T> T get(@Nullable String name, Class<T> aClass)
        {
            ClassToInstanceMap<Object> instanceMap = nameMap.get(fromNullable(name));
            if (instanceMap == null) {
                return null;
            }
            return (T) instanceMap.get(aClass);
        }
    }
}
