package com.proofpoint.http.server;

import com.proofpoint.tracetoken.TraceTokenManager;

import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

class TraceTokenFilter
        implements Filter
{
    private final TraceTokenManager traceTokenManager;

    @Inject
    public TraceTokenFilter(TraceTokenManager traceTokenManager)
    {
        this.traceTokenManager = traceTokenManager;
    }

    @Override
    public void init(FilterConfig filterConfig)
            throws ServletException
    {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException
    {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String token = request.getHeader("X-Proofpoint-TraceToken");
        if (token != null) {
            traceTokenManager.registerRequestToken(token);
        }
        else {
            traceTokenManager.createAndRegisterNewRequestToken();
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy()
    {
    }
}
