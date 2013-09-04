/*
 * Copyright 2013 Proofpoint, Inc.
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
package com.proofpoint.reporting;

import com.google.common.base.Optional;
import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.inject.Inject;
import org.weakref.jmx.MBeanExporter;
import org.weakref.jmx.ObjectNameBuilder;

import javax.annotation.Nullable;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Throwables.propagate;
import static java.lang.reflect.Proxy.newProxyInstance;

class ReportCollectionFactory
{
    private final Ticker ticker;
    private final MBeanExporter mBeanExporter;
    private final ReportExporter reportExporter;

    @Inject
    public ReportCollectionFactory(MBeanExporter mBeanExporter, ReportExporter reportExporter)
    {
        this(mBeanExporter, reportExporter, Ticker.systemTicker());
    }

    ReportCollectionFactory(MBeanExporter mBeanExporter, ReportExporter reportExporter, Ticker ticker)
    {
        this.mBeanExporter = mBeanExporter;
        this.reportExporter = reportExporter;
        this.ticker = ticker;
    }

    public <T> T createReportCollection(Class<T> aClass)
    {
        return createReportCollection(aClass, null);
    }

    @SuppressWarnings("unchecked")
    public <T> T createReportCollection(Class<T> aClass, @Nullable String name)
    {
        return (T) newProxyInstance(aClass.getClassLoader(),
                new Class[]{aClass},
                new StatInvocationHandler(aClass, name));
    }

    private class StatInvocationHandler implements InvocationHandler
    {
        private final Map<Method,LoadingCache<List<Optional<String>>,Object>> cacheMap;
        private final ConcurrentMap<Object, String> objectNameMap = new ConcurrentHashMap<>();

        public <T> StatInvocationHandler(Class<T> aClass, @Nullable String name)
        {
            Builder<Method, LoadingCache<List<Optional<String>>, Object>> cacheBuilder = ImmutableMap.builder();

            for (final Method method : aClass.getMethods()) {
                final Constructor<?> constructor;
                try {
                    constructor = method.getReturnType().getConstructor();
                }
                catch (NoSuchMethodException e) {
                    throw new RuntimeException(methodName(method) + " return type " + method.getReturnType().getSimpleName()
                            + " has no public no-arg constructor");
                }

                ImmutableList.Builder<String> keyNameBuilder = ImmutableList.builder();
                int argPosition = 0;
                for (Annotation[] annotations : method.getParameterAnnotations()) {
                    ++argPosition;
                    boolean found = false;
                    for (Annotation annotation : annotations) {
                        if (Key.class.isAssignableFrom(annotation.annotationType())) {
                            keyNameBuilder.add(((Key)annotation).value());
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        throw new RuntimeException(methodName(method) + " parameter " + argPosition
                                + " has no @com.proofpoint.reporting.Key annotation");
                    }
                }
                final String packageName;
                final Map<String, String> properties = new LinkedHashMap<>();
                if (name == null) {
                    packageName = aClass.getPackage().getName();
                    properties.put("type", aClass.getSimpleName());
                }
                else {
                    ObjectName objectName;
                    try {
                        objectName = ObjectName.getInstance(name);
                    }
                    catch (MalformedObjectNameException e) {
                        throw propagate(e);
                    }
                    packageName = objectName.getDomain();
                    int index = packageName.length();
                    if (name.charAt(index++) != ':') {
                        throw new RuntimeException("Unable to parse ObjectName " + name);
                    }
                    while (index < name.length()) {
                        int separatorIndex = name.indexOf('=', index);
                        String key = name.substring(index, separatorIndex);
                        String value;
                        if (name.charAt(++separatorIndex) == '\"') {
                            StringBuilder sb = new StringBuilder();
                            char c;
                            while ((c = name.charAt(++separatorIndex)) != '\"') {
                                if (c == '\\') {
                                    c = name.charAt(++separatorIndex);
                                }
                                sb.append(c);
                            }
                            if (name.charAt(++separatorIndex) != ',') {
                                throw new RuntimeException("Unable to parse ObjectName " + name);
                            }
                            value = sb.toString();
                            index = separatorIndex + 1;
                        }
                        else {
                            index = name.indexOf(',', separatorIndex);
                            if (index == -1) {
                                index = name.length();
                            }
                            value = name.substring(separatorIndex, index);
                        }
                        properties.put(key, value);
                    }
                }
                final String upperMethodName = LOWER_CAMEL.to(UPPER_CAMEL, method.getName());
                final List<String> keyNames = keyNameBuilder.build();

                cacheBuilder.put(method, CacheBuilder.newBuilder()
                        .ticker(ticker)
                        .expireAfterAccess(15, TimeUnit.MINUTES)
                        .removalListener(new UnexportRemovalListener())
                        .build(new CacheLoader<List<Optional<String>>, Object>()
                        {
                            @Override
                            public Object load(List<Optional<String>> key)
                                    throws Exception
                            {
                                Object stat = constructor.newInstance();

                                ObjectNameBuilder objectNameBuilder = new ObjectNameBuilder(packageName);
                                for (Entry<String, String> entry : properties.entrySet()) {
                                    objectNameBuilder = objectNameBuilder.withProperty(entry.getKey(), entry.getValue());
                                }
                                objectNameBuilder = objectNameBuilder.withProperty("name", upperMethodName);
                                for (int i = 0; i < keyNames.size(); ++i) {
                                    if (key.get(i).isPresent()) {
                                        objectNameBuilder = objectNameBuilder.withProperty(keyNames.get(i), key.get(i).get());
                                    }
                                }
                                String objectName = objectNameBuilder.build();
                                reportExporter.export(ObjectName.getInstance(objectName), stat);
                                mBeanExporter.export(objectName, stat);
                                objectNameMap.put(stat, objectName);
                                return stat;
                            }
                        })
                );
            }
            cacheMap = cacheBuilder.build();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable
        {
            ImmutableList.Builder<Optional<String>> argBuilder = ImmutableList.builder();
            for (Object arg : args) {
                if (arg == null) {
                    argBuilder.add(Optional.<String>absent());
                }
                else {
                    argBuilder.add(Optional.of(arg.toString()));
                }
            }
            return cacheMap.get(method).get(argBuilder.build());
        }

        private class UnexportRemovalListener implements RemovalListener<List<Optional<String>>, Object>
        {
            @Override
            public void onRemoval(RemovalNotification<List<Optional<String>>, Object> notification)
            {
                String objectName = objectNameMap.remove(notification.getValue());
                try {
                    reportExporter.unexport(ObjectName.getInstance(objectName));
                }
                catch (MalformedObjectNameException ignored) {
                }
                mBeanExporter.unexport(objectName);
            }
        }
    }

    private String methodName(Method method)
    {
        StringBuilder builder = new StringBuilder(method.getDeclaringClass().getName());
        builder.append(".").append(method.getName()).append('(');
        boolean first = true;
        for (Class<?> type : method.getParameterTypes()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(type.getName());
            first = false;
        }
        builder.append(')');
        return builder.toString();
    }
}
