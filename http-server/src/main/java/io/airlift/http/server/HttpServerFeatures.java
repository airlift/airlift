package io.airlift.http.server;

public record HttpServerFeatures(boolean virtualThreads, boolean legacyUriCompliance, boolean caseSensitiveHeaderCache)
{
    public static Builder builder()
    {
        return new Builder();
    }

    public static class Builder
    {
        private boolean virtualThreads;
        private boolean legacyUriCompliance;
        private boolean caseSensitiveHeaderCache;

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

        public Builder withCaseSensitiveHeaderCache(boolean caseInsensitiveHeaderCache)
        {
            this.caseSensitiveHeaderCache = caseInsensitiveHeaderCache;
            return this;
        }

        public HttpServerFeatures build()
        {
            return new HttpServerFeatures(virtualThreads, legacyUriCompliance, caseSensitiveHeaderCache);
        }
    }
}
