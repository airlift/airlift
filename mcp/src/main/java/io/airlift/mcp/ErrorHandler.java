package io.airlift.mcp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface ErrorHandler
{
    void handleException(HttpServletRequest request, HttpServletResponse response, Throwable throwable);
}
