package io.airlift.http.server;

public record HttpServerFeatures(boolean virtualThreads, boolean legacyUriCompliance, boolean http2)
{
    public static Builder builder()
    {
        return new Builder();
    }

    public static class Builder
    {
        private boolean virtualThreads;
        private boolean legacyUriCompliance;
        private boolean http2 = true;

        private Builder() {}

        public Builder withVirtualThreads(boolean virtualThreads)
        {
            this.virtualThreads = virtualThreads;
            return this;
        }

        public Builder withLegacyUriCompliance(boolean legacyUriCompliance)
        {
            this.legacyUriCompliance = legacyUriCompliance;
            return this;
        }

        public Builder withHttp2(boolean http2)
        {
            this.http2 = http2;
            return this;
        }

        public HttpServerFeatures build()
        {
            return new HttpServerFeatures(virtualThreads, legacyUriCompliance, http2);
        }
    }
}
