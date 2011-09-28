package com.proofpoint.http.client;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import java.net.URI;

public class RequestBuilder
{
    public static RequestBuilder prepareHead() {
        return new RequestBuilder().setMethod("HEAD");
    }

    public static RequestBuilder prepareGet() {
        return new RequestBuilder().setMethod("GET");
    }

    public static RequestBuilder preparePost() {
        return new RequestBuilder().setMethod("POST");
    }

    public static RequestBuilder preparePut() {
        return new RequestBuilder().setMethod("PUT");
    }

    public static RequestBuilder prepareDelete() {
        return new RequestBuilder().setMethod("DELETE");
    }

    private URI uri;
    private String method;
    private final ListMultimap<String, String> headers = ArrayListMultimap.create();
    private BodyGenerator bodyGenerator;

    public RequestBuilder setUri(URI uri)
    {
        this.uri = uri;
        return this;
    }

    public RequestBuilder setMethod(String method)
    {
        this.method = method;
        return this;
    }

    public RequestBuilder setHeader(String name, String value)
    {
        this.headers.removeAll(name);
        this.headers.put(name, value);
        return this;
    }

    public RequestBuilder addHeader(String name, String value)
    {
        this.headers.put(name, value);
        return this;
    }

    public RequestBuilder setBodyGenerator(BodyGenerator bodyGenerator)
    {
        this.bodyGenerator = bodyGenerator;
        return this;
    }

    public Request build() {
        return new Request(uri, method, headers, bodyGenerator);
    }
}
