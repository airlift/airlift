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

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Injector;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map.Entry;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.propagate;
import static com.proofpoint.reporting.AnnotationUtils.findReportedMethods;
import static com.proofpoint.reporting.AnnotationUtils.isFlatten;
import static com.proofpoint.reporting.AnnotationUtils.isNested;

class ReportExporter
{
    private final ReportedBeanRegistry registry;

    @Inject
    ReportExporter(Set<Mapping> mappings,
            ReportedBeanRegistry registry, BucketIdProvider bucketIdProvider, Injector injector)
            throws MalformedObjectNameException, InstanceAlreadyExistsException
    {
        this.registry = checkNotNull(registry, "registry is null");
        export(mappings, bucketIdProvider, injector);
    }

    private void export(Set<Mapping> mappings, BucketIdProvider bucketIdProvider, Injector injector)
            throws MalformedObjectNameException, InstanceAlreadyExistsException
    {
        for (Mapping mapping : mappings) {
            Object object = injector.getInstance(mapping.getKey());
            export(mapping.getName(), object);
            notifyBucketIdProvider(object, bucketIdProvider, null);
        }
    }

    private void export(String name, Object object)
            throws MalformedObjectNameException, InstanceAlreadyExistsException
    {
        ObjectName objectName = new ObjectName(name);
        ReportedBean reportedBean = ReportedBean.forTarget(object);
        if (!reportedBean.getAttributes().isEmpty()) {
            registry.register(reportedBean, objectName);
        }
    }

    @VisibleForTesting
    static void notifyBucketIdProvider(Object object, BucketIdProvider bucketIdProvider, Method annotatedGetter)
    {
        if (object instanceof Bucketed) {
            ((Bucketed) object).setBucketIdProvider(bucketIdProvider);
        }
        else if (annotatedGetter != null && !isNested(annotatedGetter) && !isFlatten(annotatedGetter)) {
            return;
        }

        try {
            for (Entry<Method, Method> entry : findReportedMethods(object.getClass()).entrySet()) {
                notifyBucketIdProvider(entry.getKey().invoke(object), bucketIdProvider, entry.getValue());
            }
        }
        catch (IllegalAccessException | InvocationTargetException e) {
            throw propagate(e);
        }
    }
}
