package com.proofpoint.jetty;

import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.util.RolloverFileOutputStream;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.joda.time.format.ISODateTimeFormat;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.security.Principal;

class DelimitedRequestLog
        implements RequestLog
{
    // Tab-separated
    // Time, ip, method, url, user, agent, response code, response length, response time

    private final RolloverFileOutputStream out;
    private final Writer writer;

    private final DateTimeFormatter isoFormatter;


    public DelimitedRequestLog(String filename, int retainDays)
            throws IOException
    {
        out = new RolloverFileOutputStream(filename, true, retainDays);
        writer = new OutputStreamWriter(out);

        isoFormatter = new DateTimeFormatterBuilder()
                .append(ISODateTimeFormat.dateHourMinuteSecondFraction())
                .appendTimeZoneOffset("Z", true, 2, 2)
                .toFormatter();
    }

    public void log(Request request, Response response)
    {
        StringBuilder builder = new StringBuilder();

        String user = "";
        Principal principal = request.getUserPrincipal();
        if (principal != null) {
            user = principal.getName();
        }

        String agent = request.getHeader("User-Agent");
        if (agent == null) {
            agent = "";
        }

        builder.append(isoFormatter.print(request.getTimeStamp()))
                .append('\t')
                .append(request.getRemoteAddr()) // TODO: handle X-Forwarded-For
                .append('\t')
                .append(request.getMethod().toUpperCase())
                .append('\t')
                .append(request.getUri()) // TODO: escape
                .append('\t')
                .append(user)
                .append('\t')
                .append(agent) // TODO: escape
                .append('\t')
                .append(response.getStatus())
                .append('\t')
                .append(response.getContentCount())
                .append('\t')
                .append(getRequestTime(request))
                .append('\n');

        String line = builder.toString();
        synchronized (writer) {
            try {
                writer.write(line);
                writer.flush();
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected long getRequestTime(Request request)
    {
        // TODO: use nanoseconds or floating point seconds
        return System.currentTimeMillis() - request.getTimeStamp();
    }

    public void start() throws Exception
    {
    }

    public void stop() throws Exception
    {
        out.close();
    }

    public boolean isRunning()
    {
        return true;
    }

    public boolean isStarted()
    {
        return true;
    }

    public boolean isStarting()
    {
        return false;
    }

    public boolean isStopping()
    {
        return false;
    }

    public boolean isStopped()
    {
        return false;
    }

    public boolean isFailed()
    {
        return false;
    }

    public void addLifeCycleListener(Listener listener)
    {
    }

    public void removeLifeCycleListener(Listener listener)
    {
    }
}
