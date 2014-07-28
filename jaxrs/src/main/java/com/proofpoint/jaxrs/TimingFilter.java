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
package com.proofpoint.jaxrs;

import com.proofpoint.units.Duration;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;

import static com.google.common.base.Preconditions.checkNotNull;

class TimingFilter
        implements ContainerRequestFilter, ContainerResponseFilter
{
    private static final String START_TIME_KEY = TimingFilter.class.getName() + ".start-time";
    private final String methodName;
    private final RequestStats requestStats;

    TimingFilter(String methodName, RequestStats requestStats)
    {
        this.methodName = checkNotNull(methodName, "methodName is null");
        this.requestStats = checkNotNull(requestStats, "requestStats is null");
    }

    @Override
    public void filter(ContainerRequestContext request)
    {
        request.setProperty(START_TIME_KEY, System.nanoTime());
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response)
    {
        Long startTime = (Long) request.getProperty(START_TIME_KEY);
        requestStats.requestTime(methodName, response.getStatus())
                .add(Duration.nanosSince(startTime));
    }
}
