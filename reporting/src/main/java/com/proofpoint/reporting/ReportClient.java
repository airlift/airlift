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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.google.inject.Inject;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;

import javax.management.ObjectName;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.proofpoint.http.client.HttpUriBuilder.uriBuilderFrom;
import static com.proofpoint.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static com.proofpoint.http.client.Request.Builder.preparePost;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.json.JsonCodec.listJsonCodec;

class ReportClient
{
    private static final Logger logger = Logger.get(ReportClient.class);
    private static final JsonCodec<List<DataPoint>> dataPointListCodec = listJsonCodec(DataPoint.class);
    private final Map<String, String> instanceTags;
    private final HttpClient httpClient;
    private final URI uploadUri;

    @Inject
    ReportClient(NodeInfo nodeInfo, @ForReportClient HttpClient httpClient, ReportClientConfig reportClientConfig)
    {

        checkNotNull(nodeInfo, "nodeInfo is null");

        Builder<String, String> builder = ImmutableMap.builder();
        builder.put("host", nodeInfo.getInternalHostname());
        builder.put("environment", nodeInfo.getEnvironment());
        builder.put("pool", nodeInfo.getPool());
        this.instanceTags = builder.build();

        this.httpClient = checkNotNull(httpClient, "httpClient is null");

        if (reportClientConfig.getUri() == null) {
            uploadUri = null;
        }
        else {
            uploadUri = uriBuilderFrom(reportClientConfig.getUri())
                    .appendPath("api/v1/datapoints")
                    .build();
        }
    }

    public void report(long systemTimeMillis, Table<ObjectName, String, Number> collectedData)
    {
        if (uploadUri == null) {
            return;
        }

        List<DataPoint> dataPoints = new ArrayList<>();
        for (Cell<ObjectName, String, Number> cell : collectedData.cellSet()) {
            dataPoints.add(new DataPoint(systemTimeMillis, cell, instanceTags));
        }

        Request request = preparePost()
                .setUri(uploadUri)
                .setBodyGenerator(jsonBodyGenerator(dataPointListCodec, dataPoints))
                .build();
        StatusResponse response = httpClient.execute(request, createStatusResponseHandler());
        if (response.getStatusCode() != 204) {
            logger.warn("Failed to report stats: %s %s", response.getStatusCode(), response.getStatusMessage());
        }
    }

    private static class DataPoint
    {
        @JsonProperty
        private final String name;
        @JsonProperty
        private final long timestamp;
        @JsonProperty
        private final Number value;
        @JsonProperty
        private final Map<String, String> tags;

        public DataPoint(long systemTimeMillis, Cell<ObjectName, String, Number> cell, Map<String, String> instanceTags)
        {
            name = cell.getColumnKey();
            timestamp = systemTimeMillis;
            value = cell.getValue();
            tags = ImmutableMap.<String, String>builder()
                    .putAll(instanceTags)
                    .putAll(cell.getRowKey().getKeyPropertyList())
                    .build();
        }
    }
}
