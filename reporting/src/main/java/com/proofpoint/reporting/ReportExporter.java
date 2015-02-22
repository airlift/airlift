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
import com.proofpoint.reporting.ReportException.Reason;
import org.weakref.jmx.MBeanExporter;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map.Entry;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.propagate;
import static com.proofpoint.reporting.AnnotationUtils.findReportedMethods;
import static com.proofpoint.reporting.AnnotationUtils.isFlatten;
import static com.proofpoint.reporting.AnnotationUtils.isNested;

public class ReportExporter
{
    private final ReportedBeanRegistry registry;
    private final BucketIdProvider bucketIdProvider;
    private final MBeanExporter mBeanExporter;

    @Inject
    ReportExporter(ReportedBeanRegistry registry, BucketIdProvider bucketIdProvider, MBeanExporter mBeanExporter)
            throws MalformedObjectNameException, InstanceAlreadyExistsException
    {
        this.registry = checkNotNull(registry, "registry is null");
        this.bucketIdProvider = checkNotNull(bucketIdProvider, "bucketIdProvider is null");
        this.mBeanExporter = checkNotNull(mBeanExporter, "mBeanExporter is null");
    }

    public void export(String name, Object object)
    {
        ObjectName objectName;
        try {
            objectName = new ObjectName(name);
        }
        catch (MalformedObjectNameException e) {
            throw new ReportException(Reason.MALFORMED_OBJECT_NAME, e.getMessage());
        }

        export(objectName, object);
    }

    public void export(ObjectName objectName, Object object)
    {
        ReportedBean reportedBean = ReportedBean.forTarget(object);
        notifyBucketIdProvider(object, bucketIdProvider, null);
        if (!reportedBean.getAttributes().isEmpty()) {
            try {
                registry.register(reportedBean, objectName);
            }
            catch (InstanceAlreadyExistsException e) {
                throw new ReportException(Reason.INSTANCE_ALREADY_EXISTS, e.getMessage());
            }
        }
        mBeanExporter.export(objectName, object);
    }

    public void unexport(String name)
    {
        ObjectName objectName;

        try {
            objectName = new ObjectName(name);
        }
        catch (MalformedObjectNameException e) {
            throw new ReportException(Reason.MALFORMED_OBJECT_NAME, e.getMessage());
        }

        unexport(objectName);
    }

    public void unexport(ObjectName objectName)
    {
        try {
            registry.unregister(objectName);
        }
        catch (InstanceNotFoundException e) {
            throw new ReportException(Reason.INSTANCE_NOT_FOUND, e.getMessage());
        }
        mBeanExporter.unexport(objectName);
    }

    @VisibleForTesting
    static void notifyBucketIdProvider(Object object, BucketIdProvider bucketIdProvider, Method annotatedGetter)
    {
        if (object instanceof Bucketed) {
            ((Bucketed<?>) object).setBucketIdProvider(bucketIdProvider);
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
