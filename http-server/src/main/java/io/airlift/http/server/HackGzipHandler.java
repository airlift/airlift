package io.airlift.http.server;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;

import java.util.zip.Deflater;

// TODO: remove this when fixed in Jetty
public class HackGzipHandler
        extends GzipHandler
{
    @Override
    public Deflater getDeflater(Request request, long content_length)
    {
        // GzipHandler incorrectly skips this check for HTTP/2
        HttpField accept = request.getHttpFields().getField(HttpHeader.ACCEPT_ENCODING);
        if ((accept == null) || !accept.contains("gzip")) {
            return null;
        }
        return super.getDeflater(request, content_length);
    }
}
