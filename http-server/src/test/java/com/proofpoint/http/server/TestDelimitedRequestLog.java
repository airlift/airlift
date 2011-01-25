package com.proofpoint.http.server;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.joda.time.format.ISODateTimeFormat;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.security.Principal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestDelimitedRequestLog
{
    private File file;
    private DateTimeFormatter isoFormatter;

    @BeforeMethod
    public void setup()
            throws IOException
    {
        file = File.createTempFile(getClass().getName(), ".log");

        isoFormatter = new DateTimeFormatterBuilder()
                .append(ISODateTimeFormat.dateHourMinuteSecondFraction())
                .appendTimeZoneOffset("Z", true, 2, 2)
                .toFormatter();
    }

    @AfterMethod
    public void teardown()
            throws IOException
    {
        if (!file.delete()) {
            throw new IOException("Error deleting " + file.getAbsolutePath());
        }
    }

    @Test
    public void testWriteLog()
            throws Exception
    {
        final Request request = mock(Request.class);
        final Response response = mock(Response.class);
        final Principal principal = mock(Principal.class);

        final long requestTime = 3453;
        final long now = System.currentTimeMillis();
        final long timestamp = now - requestTime;
        final String user = "martin";
        final String agent = "HttpClient 4.0";
        final String ip = "10.54.12.111";
        final String method = "GET";
        final int status = 200;
        final long contentLength = 32311;
        final HttpURI uri = new HttpURI("http://www.example.com/aaa+bbb/ccc?param=hello%20there&other=true");


        DelimitedRequestLog logger = new DelimitedRequestLog(file.getAbsolutePath(), 1)
        {
            @Override
            protected long getRequestTime(Request request)
            {
                return requestTime;
            }
        };

        when(principal.getName()).thenReturn(user);
        when(request.getTimeStamp()).thenReturn(timestamp);
        when(request.getHeader("User-Agent")).thenReturn(agent);
        when(request.getRemoteAddr()).thenReturn(ip);
        when(request.getUri()).thenReturn(uri);
        when(request.getUserPrincipal()).thenReturn(principal);
        when(request.getMethod()).thenReturn(method);
        when(response.getStatus()).thenReturn(status);
        when(response.getContentCount()).thenReturn(contentLength);

        logger.log(request, response);
        logger.stop();

        String actual = Files.toString(file, Charsets.UTF_8);
        String expected = String.format("%s\t%s\t%s\t%s\t%s\t%s\t%d\t%d\t%d\n",
                isoFormatter.print(timestamp), ip, method, uri, user, agent, status, contentLength, requestTime);
        Assert.assertEquals(actual, expected);
    }
}
