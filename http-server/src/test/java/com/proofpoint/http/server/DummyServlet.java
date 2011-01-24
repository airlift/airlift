package com.proofpoint.http.server;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

class DummyServlet
    extends HttpServlet
{
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException
    {
        resp.setStatus(HttpServletResponse.SC_OK);
        if (req.getUserPrincipal() != null) {
            resp.getOutputStream().write(req.getUserPrincipal().getName().getBytes());
        }
    }
}
