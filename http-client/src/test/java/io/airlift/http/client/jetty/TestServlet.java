package io.airlift.http.client.jetty;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

import static javax.servlet.http.HttpServletResponse.SC_OK;

public class TestServlet
        extends HttpServlet
{
    public TestServlet() {}

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException
    {
        response.setContentType("text/html");
        response.setStatus(SC_OK);
        response.getWriter().println("test");
    }
}
