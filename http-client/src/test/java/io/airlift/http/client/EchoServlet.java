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

import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.ByteStreams;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpUtils;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Map;

public final class EchoServlet
        extends HttpServlet
{
    String requestMethod;
    URI requestUri;
    final ListMultimap<String, String> requestHeaders = ArrayListMultimap.create();
    byte[] requestBytes;

    int responseStatusCode = 200;
    String responseStatusMessage;
    final ListMultimap<String, String> responseHeaders = ArrayListMultimap.create();
    String responseBody;

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        requestMethod = request.getMethod();
        requestUri = URI.create(HttpUtils.getRequestURL(request).toString());
        if (request.getQueryString() != null) {
            requestUri = URI.create(requestUri.toASCIIString() + "?" + request.getQueryString());
        }

        requestHeaders.clear();
        for (String name : Collections.list(request.getHeaderNames())) {
            requestHeaders.putAll(name, Collections.list(request.getHeaders(name)));
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
            response.getOutputStream().write(responseBody.getBytes(Charsets.UTF_8));
        }
    }
}
