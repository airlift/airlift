/*
 * Copyright 2010 Proofpoint, Inc.
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
package io.airlift.http.client;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.ByteStreams;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class EchoServlet
        extends HttpServlet
{
    private String requestMethod;
    private URI requestUri;
    private final ListMultimap<HeaderName, String> requestHeaders = ArrayListMultimap.create();
    private byte[] requestBytes;

    private int responseStatusCode = 200;
    private String responseStatusMessage;
    private final ListMultimap<String, String> responseHeaders = ArrayListMultimap.create();
    private String responseBody;

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        requestMethod = request.getMethod();
        requestUri = URI.create(request.getRequestURL().toString());
        if (request.getQueryString() != null) {
            requestUri = URI.create(requestUri.toASCIIString() + "?" + request.getQueryString());
        }

        requestHeaders.clear();
        for (String name : Collections.list(request.getHeaderNames())) {
            requestHeaders.putAll(HeaderName.of(name), Collections.list(request.getHeaders(name)));
        }

        requestBytes = ByteStreams.toByteArray(request.getInputStream());

        if (responseStatusMessage != null) {
            response.sendError(responseStatusCode, responseStatusMessage);
        }
        else {
            response.setStatus(responseStatusCode);
        }
        for (Map.Entry<String, String> entry : responseHeaders.entries()) {
            response.addHeader(entry.getKey(), entry.getValue());
        }

        try {
            if (request.getParameter("sleep") != null) {
                Thread.sleep(Long.parseLong(request.getParameter("sleep")));
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        if (request.getParameter("remotePort") != null) {
            response.addHeader("remotePort", String.valueOf(request.getRemotePort()));
        }

        if (responseBody != null) {
            response.getOutputStream().write(responseBody.getBytes(UTF_8));
        }
    }

    public String getRequestMethod()
    {
        return requestMethod;
    }

    public URI getRequestUri()
    {
        return requestUri;
    }

    public ListMultimap<HeaderName, String> getRequestHeaders()
    {
        return ImmutableListMultimap.copyOf(requestHeaders);
    }

    public List<String> getRequestHeaders(String name)
    {
        return requestHeaders.get(HeaderName.of(name));
    }

    public byte[] getRequestBytes()
    {
        return requestBytes.clone();
    }

    public void setResponseStatusCode(int responseStatusCode)
    {
        this.responseStatusCode = responseStatusCode;
    }

    public void setResponseStatusMessage(String responseStatusMessage)
    {
        this.responseStatusMessage = responseStatusMessage;
    }

    public void addResponseHeader(String name, String value)
    {
        this.responseHeaders.put(name, value);
    }

    public void setResponseBody(String responseBody)
    {
        this.responseBody = responseBody;
    }
}
