package io.airlift.http.client;

import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

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

        requestHeaders.clear();
        for (String name : Collections.list(request.getHeaderNames())) {
            requestHeaders.putAll(name, Collections.list(request.getHeaders(name)));
        }

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
