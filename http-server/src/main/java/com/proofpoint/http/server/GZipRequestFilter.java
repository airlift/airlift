package com.proofpoint.http.server;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.apache.commons.lang.StringUtils.equalsIgnoreCase;

public class GZipRequestFilter
        implements Filter
{
    public void init(FilterConfig filterConfig) throws ServletException
    {
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException
    {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String contentEncoding = request.getHeader("content-encoding");
        if (equalsIgnoreCase(contentEncoding, "gzip")) {
            filterChain.doFilter(new GZipRequestWrapper(request), servletResponse);
        }
        else {
            filterChain.doFilter(request, response);
        }
    }

    public void destroy()
    {
    }
}
