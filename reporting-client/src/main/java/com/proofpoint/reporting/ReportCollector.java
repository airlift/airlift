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

import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;

import javax.annotation.PostConstruct;
import javax.management.AttributeNotFoundException;
import javax.management.MBeanException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

class ReportCollector
{
    private ScheduledExecutorService collectionExecutorService = newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("reporting-collector-%s").setDaemon(true).build());
    private MinuteBucketIdProvider bucketIdProvider;
    private ReportedBeanRegistry reportedBeanRegistry;
    private ExecutorService clientExecutorService;
    private ReportClient reportClient;

    @Inject
    ReportCollector(MinuteBucketIdProvider bucketIdProvider, ReportedBeanRegistry reportedBeanRegistry, ReportClient reportClient)
    {
        this.bucketIdProvider = checkNotNull(bucketIdProvider, "bucketIdProvider is null");
        this.reportedBeanRegistry = checkNotNull(reportedBeanRegistry, "reportedBeanRegistry is null");
        this.reportClient = checkNotNull(reportClient, "reportClient is null");
        clientExecutorService = new ThreadPoolExecutor(1, 1, 0, TimeUnit.NANOSECONDS, new LinkedBlockingQueue<Runnable>(),
                new ThreadFactoryBuilder().setNameFormat("reporting-client-%s").setDaemon(true).build(),
                new DiscardOldestPolicy());
    }

    @PostConstruct
    public void start()
    {
        collectionExecutorService.scheduleAtFixedRate(new Runnable()
        {
            @Override
            public void run()
            {
                collectData();
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    private void collectData()
    {
        final long lastSystemTimeMillis = bucketIdProvider.getLastSystemTimeMillis();
        ImmutableTable.Builder<ObjectName, String, Number> builder = ImmutableTable.builder();
        for (Entry<ObjectName, ReportedBean> reportedBeanEntry : reportedBeanRegistry.getReportedBeans().entrySet()) {
            for (ReportedBeanAttribute attribute : reportedBeanEntry.getValue().getAttributes()) {
                Number value = null;

                try {
                    value = attribute.getValue(null);
                }
                catch (AttributeNotFoundException | MBeanException | ReflectionException ignored) {
                }

                if (isReportable(value)) {
                    builder.put(reportedBeanEntry.getKey(), attribute.getName(), value);
                }
            }
        }
        final Table<ObjectName, String, Number> collectedData = builder.build();
        clientExecutorService.submit(new Runnable()
        {
            @Override
            public void run()
            {
                reportClient.report(lastSystemTimeMillis, collectedData);
            }
        });
    }

    private static boolean isReportable(Number value)
    {
        if (value == null) {
            return false;
        }
        if (value instanceof Double) {
            return !(((Double) value).isNaN() || ((Double) value).isInfinite());
        }
        if (value instanceof Float) {
            return !(((Float) value).isNaN() || ((Float) value).isInfinite());
        }
        if (value instanceof Long) {
            return !(value.equals(Long.MAX_VALUE) || value.equals(Long.MIN_VALUE));
        }
        if (value instanceof Integer) {
            return !(value.equals(Integer.MAX_VALUE) || value.equals(Integer.MIN_VALUE));
        }
        if (value instanceof Short) {
            return !(value.equals(Short.MAX_VALUE) || value.equals(Short.MIN_VALUE));
        }
        return true;
    }
}
