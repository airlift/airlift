package com.proofpoint.http.client;

import com.google.inject.Inject;
import com.proofpoint.tracetoken.TraceTokenManager;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.proofpoint.http.client.Request.Builder.fromRequest;

public class TraceTokenRequestFilter
        implements HttpRequestFilter
{
    public static final String TRACETOKEN_HEADER = "X-Proofpoint-Tracetoken";
    private final TraceTokenManager traceTokenManager;

    @Inject
    public TraceTokenRequestFilter(TraceTokenManager traceTokenManager)
    {
        this.traceTokenManager = checkNotNull(traceTokenManager, "traceTokenManager is null");
    }

    @Override
    public Request filterRequest(Request request)
    {
        checkNotNull(request, "request is null");

        String token = traceTokenManager.getCurrentRequestToken();
        if (token == null) {
            return request;
        }

        return fromRequest(request)
                .addHeader(TRACETOKEN_HEADER, token)
                .build();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }
        TraceTokenRequestFilter o = (TraceTokenRequestFilter) obj;
        return traceTokenManager.equals(o.traceTokenManager);
    }

    @Override
    public int hashCode()
    {
        return traceTokenManager.hashCode();
    }
}
