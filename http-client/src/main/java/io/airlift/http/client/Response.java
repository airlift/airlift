package io.airlift.http.client;

import com.google.common.annotations.Beta;
import com.google.common.collect.ListMultimap;

import java.io.IOException;
import java.io.InputStream;

@Beta
public interface Response
{
    int getStatusCode();

    String getStatusMessage();

    String getHeader(String name);

    ListMultimap<String, String> getHeaders();

    long getBytesRead();

    InputStream getInputStream()
            throws IOException;
}
