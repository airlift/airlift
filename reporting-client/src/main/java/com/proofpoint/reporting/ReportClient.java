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
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.google.inject.Inject;
import com.proofpoint.http.client.BodyGenerator;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;

import javax.management.ObjectName;
import java.io.OutputStream;
import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.proofpoint.http.client.HttpUriBuilder.uriBuilderFrom;
import static com.proofpoint.http.client.Request.Builder.preparePost;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;

class ReportClient
{
    private static final Logger logger = Logger.get(ReportClient.class);
    private static final JsonFactory JSON_FACTORY = new JsonFactory();
    private final Map<String, String> instanceTags;
    private final HttpClient httpClient;
    private final URI uploadUri;
    private final ObjectMapper objectMapper;

    @Inject
    ReportClient(NodeInfo nodeInfo, @ForReportClient HttpClient httpClient, ReportClientConfig reportClientConfig, ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
        checkNotNull(nodeInfo, "nodeInfo is null");
        checkNotNull(reportClientConfig, "reportClientConfig is null");

        Builder<String, String> builder = ImmutableMap.builder();
        builder.put("application", nodeInfo.getApplication());
        builder.put("host", nodeInfo.getInternalHostname());
        builder.put("environment", nodeInfo.getEnvironment());
        builder.put("pool", nodeInfo.getPool());
        builder.putAll(reportClientConfig.getTags());
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

        Request request = preparePost()
                .setUri(uploadUri)
                .setHeader("Content-Type", "application/gzip")
                .setBodyGenerator(new CompressBodyGenerator(systemTimeMillis, collectedData))
                .build();
        StatusResponse response = httpClient.execute(request, createStatusResponseHandler());
        if (response.getStatusCode() != 204) {
            logger.warn("Failed to report stats: %s %s", response.getStatusCode(), response.getStatusMessage());
        }
    }

    private static class DataPoint
    {
        private static final Pattern QUOTED_PATTERN = Pattern.compile("\"(.*)\"");
        private static final Pattern BACKQUOTE_PATTERN = Pattern.compile("\\\\(.)");
        private static final Pattern NOT_ACCEPTED_CHARACTER_PATTERN = Pattern.compile("[^-A-Za-z0-9./_]");
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
            Builder<String, String> builder = ImmutableMap.<String, String>builder()
                    .putAll(instanceTags)
                    .put("package", cell.getRowKey().getDomain());
            for (Entry<String, String> entry : cell.getRowKey().getKeyPropertyList().entrySet()) {
                Matcher matcher = QUOTED_PATTERN.matcher(entry.getValue());
                String dequoted;
                if (matcher.matches()) {
                    dequoted = BACKQUOTE_PATTERN.matcher(matcher.group(1)).replaceAll("$1");
                }
                else {
                    dequoted = entry.getValue();
                }
                builder.put(entry.getKey(), NOT_ACCEPTED_CHARACTER_PATTERN.matcher(dequoted).replaceAll("_"));
            }
            tags = builder.build();
        }
    }

    private class CompressBodyGenerator implements BodyGenerator
    {
        private final long systemTimeMillis;
        private final Table<ObjectName,String,Number> collectedData;

        public CompressBodyGenerator(long systemTimeMillis, Table<ObjectName, String, Number> collectedData)
        {
            this.systemTimeMillis = systemTimeMillis;
            this.collectedData = collectedData;
        }

        @Override
        public void write(OutputStream out)
                throws Exception
        {
            GZIPOutputStream gzipOutputStream = new GZIPOutputStream(out);
            JsonGenerator generator = JSON_FACTORY.createGenerator(gzipOutputStream, JsonEncoding.UTF8)
                .setCodec(objectMapper);

            generator.writeStartArray();

            for (Cell<ObjectName, String, Number> cell : collectedData.cellSet()) {
                generator.writeObject(new DataPoint(systemTimeMillis, cell, instanceTags));
            }

            generator.writeEndArray();
            generator.flush();
            gzipOutputStream.finish();
        }
    }
}
