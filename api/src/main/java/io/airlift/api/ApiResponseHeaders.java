package io.airlift.api;

import com.google.common.collect.Multimap;

public interface ApiResponseHeaders
{
    Multimap<String, String> headers();
}
