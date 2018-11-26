/*
 * Copyright (C) 2012 Ness Computing, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.http.server;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Serves files from a given folder on the classpath through jetty.
 * Intended to serve a couple of static files e.g. for javascript or HTML.
 */
// Forked from https://github.com/NessComputing/components-ness-httpserver/
public class ClassPathResourceHandler
        extends AbstractHandler
{
    private static final MimeTypes MIME_TYPES;

    static {
        MIME_TYPES = new MimeTypes();
        // Now here is an oversight... =:-O
        MIME_TYPES.addMimeMapping("json", "application/json");
    }

    private final String baseUri;
    private final String classPathResourceBase;
    private final List<String> welcomeFiles;

    public ClassPathResourceHandler(String baseUri, String classPathResourceBase, String... welcomeFiles)
    {
        this(baseUri, classPathResourceBase, ImmutableList.copyOf(welcomeFiles));
    }

    public ClassPathResourceHandler(String baseUri, String classPathResourceBase, List<String> welcomeFiles)
    {
        requireNonNull(baseUri, "baseUri is null");
        requireNonNull(classPathResourceBase, "classPathResourceBase is null");
        requireNonNull(welcomeFiles, "welcomeFiles is null");

        baseUri = baseUri.startsWith("/") ? baseUri : '/' + baseUri;
        baseUri = baseUri.endsWith("/") ? baseUri.substring(baseUri.length() - 1) : baseUri;
        this.baseUri = baseUri;

        this.classPathResourceBase = classPathResourceBase;

        ImmutableList.Builder<String> files = ImmutableList.builder();
        for (String welcomeFile : welcomeFiles) {
            if (!welcomeFile.startsWith("/")) {
                welcomeFile = "/" + welcomeFile;
            }
            files.add(welcomeFile);
        }
        this.welcomeFiles = files.build();
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException
    {
        if (baseRequest.isHandled()) {
            return;
        }

        URL resource = getResourcePath(request);
        if (resource == null) {
            return;
        }

        // When a request hits this handler, it will serve something. Either data or an error.
        baseRequest.setHandled(true);

        String method = request.getMethod();
        boolean skipContent = false;
        if (!HttpMethod.GET.is(method)) {
            if (HttpMethod.HEAD.is(method)) {
                skipContent = true;
            }
            else {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return;
            }
        }

        InputStream resourceStream = null;
        try {
            resourceStream = resource.openStream();

            String contentType = MIME_TYPES.getMimeByExtension(resource.toString());
            response.setContentType(contentType);

            if (skipContent) {
                return;
            }

            ByteStreams.copy(resourceStream, response.getOutputStream());
        }
        finally {
            closeQuietly(resourceStream);
        }
    }

    private URL getResourcePath(HttpServletRequest request)
    {
        String pathInfo = request.getPathInfo();

        // Only serve the content if the request matches the base path.
        if (pathInfo == null || !pathInfo.startsWith(baseUri)) {
            return null;
        }

        // chop off the base uri
        if (!baseUri.equals("/")) {
            pathInfo = pathInfo.substring(baseUri.length());
        }

        if (!pathInfo.startsWith("/") && !pathInfo.isEmpty()) {
            // basepath is /foo and request went to /foobar --> pathInfo starts with bar
            // basepath is /foo and request went to /foo --> pathInfo should be /index.html
            return null;
        }

        // add missing leading slash
        if (!pathInfo.startsWith("/")) {
            pathInfo = "/";
        }

        if (!"/".equals(pathInfo)) {
            String resourcePath = classPathResourceBase + pathInfo;
            return getClass().getClassLoader().getResource(resourcePath);
        }

        // check welcome files
        for (String welcomeFile : welcomeFiles) {
            String resourcePath = classPathResourceBase + welcomeFile;
            URL resource = getClass().getClassLoader().getResource(resourcePath);
            if (resource != null) {
                return resource;
            }
        }
        return null;
    }

    private static void closeQuietly(@Nullable InputStream in)
    {
        if (in != null) {
            try {
                in.close();
            }
            catch (IOException e) {
                // ignored
            }
        }
    }
}
