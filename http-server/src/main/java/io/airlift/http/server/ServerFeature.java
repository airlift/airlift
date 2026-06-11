package io.airlift.http.server;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

public enum ServerFeature
{
    VIRTUAL_THREADS,
    LEGACY_URI_COMPLIANCE,
    CASE_SENSITIVE_HEADER_CACHE,
    REQUEST_CANCELLATION;

    public static Set<ServerFeature> defaults()
    {
        return ServerFeature.builder().build();
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static class Builder
    {
        private boolean virtualThreads;
        private boolean legacyUriCompliance;
        private boolean caseSensitiveHeaderCache;
        private boolean requestCancellation;

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

        public Builder withCaseSensitiveHeaderCache(boolean caseSensitiveHeaderCache)
        {
            this.caseSensitiveHeaderCache = caseSensitiveHeaderCache;
            return this;
        }

        public Builder withRequestCancellation(boolean requestCancellation)
        {
            this.requestCancellation = requestCancellation;
            return this;
        }

        public Set<ServerFeature> build()
        {
            ImmutableSet.Builder<ServerFeature> features = ImmutableSet.builder();
            if (virtualThreads) {
                features.add(VIRTUAL_THREADS);
            }
            if (legacyUriCompliance) {
                features.add(LEGACY_URI_COMPLIANCE);
            }
            if (caseSensitiveHeaderCache) {
                features.add(CASE_SENSITIVE_HEADER_CACHE);
            }
            if (requestCancellation) {
                features.add(REQUEST_CANCELLATION);
            }
            return features.build();
        }
    }
}
