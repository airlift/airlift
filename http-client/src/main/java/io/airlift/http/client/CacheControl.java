/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.airlift.http.client;


import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// This code was forked from Apache CXF CacheControl and CacheControlHeaderProvider
public class CacheControl
{
    private static final String SEPARATOR = ",";

    private static final String COMPLEX_HEADER_EXPRESSION = "(([\\w-]+=\"[^\"]*\")|([\\w-]+=[\\w]+)|([\\w-]+))";
    private static final Pattern COMPLEX_HEADER_PATTERN = Pattern.compile(COMPLEX_HEADER_EXPRESSION);

    private static final String PUBLIC = "public";
    private static final String PRIVATE = "private";
    private static final String NO_CACHE = "no-cache";
    private static final String NO_STORE = "no-store";
    private static final String NO_TRANSFORM = "no-transform";
    private static final String MUST_REVALIDATE = "must-revalidate";
    private static final String PROXY_REVALIDATE = "proxy-revalidate";
    private static final String MAX_AGE = "max-age";
    private static final String SMAX_AGE = "s-maxage";

    private int maxAge = -1;
    private int sMaxAge = -1;
    private boolean isPrivate = false;
    private boolean noCache = false;
    private boolean noStore = false;
    private boolean noTransform = true;
    private boolean mustRevalidate = false;
    private boolean proxyRevalidate = false;
    private Map<String, String> cacheExtensions = null;
    private List<String> noCacheFields = null;
    private List<String> privateFields = null;

    public CacheControl()
    {
    }

    public Map<String, String> getCacheExtension()
    {
        if (cacheExtensions == null) {
            cacheExtensions = new HashMap<>();
        }
        return cacheExtensions;
    }

    public int getMaxAge()
    {
        return maxAge;
    }

    public List<String> getNoCacheFields()
    {
        if (noCacheFields == null) {
            noCacheFields = new ArrayList<>();
        }
        return noCacheFields;
    }

    public List<String> getPrivateFields()
    {
        if (privateFields == null) {
            privateFields = new ArrayList<>();
        }
        return privateFields;
    }

    public int getSMaxAge()
    {
        return sMaxAge;
    }

    public boolean isMustRevalidate()
    {
        return mustRevalidate;
    }

    public boolean isNoCache()
    {
        return noCache;
    }

    public boolean isNoStore()
    {
        return noStore;
    }

    public boolean isNoTransform()
    {
        return noTransform;
    }

    public boolean isPrivate()
    {
        return isPrivate;
    }

    public boolean isProxyRevalidate()
    {
        return proxyRevalidate;
    }

    public void setMaxAge(int maxAge)
    {
        this.maxAge = maxAge;
    }

    public void setMustRevalidate(boolean mustRevalidate)
    {
        this.mustRevalidate = mustRevalidate;
    }

    public void setNoCache(boolean noCache)
    {
        this.noCache = noCache;
    }

    public void setNoStore(boolean noStore)
    {
        this.noStore = noStore;
    }

    public void setNoTransform(boolean noTransform)
    {
        this.noTransform = noTransform;
    }

    public void setPrivate(boolean isPrivate)
    {
        this.isPrivate = isPrivate;
    }

    public void setProxyRevalidate(boolean proxyRevalidate)
    {
        this.proxyRevalidate = proxyRevalidate;
    }

    public void setSMaxAge(int sMaxAge)
    {
        this.sMaxAge = sMaxAge;
    }

    public static CacheControl valueOf(String string)
    {
        CacheControl cacheControl = new CacheControl();

        // for some reason the default in cache control is true
        cacheControl.setNoTransform(false);

        List<String> tokens = getTokens(string);
        for (String rawToken : tokens) {
            String token = rawToken.trim();
            if (token.startsWith(MAX_AGE)) {
                cacheControl.setMaxAge(Integer.parseInt(token.substring(MAX_AGE.length() + 1)));
            }
            else if (token.startsWith(SMAX_AGE)) {
                cacheControl.setSMaxAge(Integer.parseInt(token.substring(SMAX_AGE.length() + 1)));
            }
            else if (token.startsWith(PUBLIC)) {
                // ignore
            }
            else if (token.startsWith(NO_STORE)) {
                cacheControl.setNoStore(true);
            }
            else if (token.startsWith(NO_TRANSFORM)) {
                cacheControl.setNoTransform(true);
            }
            else if (token.startsWith(MUST_REVALIDATE)) {
                cacheControl.setMustRevalidate(true);
            }
            else if (token.startsWith(PROXY_REVALIDATE)) {
                cacheControl.setProxyRevalidate(true);
            }
            else if (token.startsWith(PRIVATE)) {
                cacheControl.setPrivate(true);
                addFields(cacheControl.getPrivateFields(), token);
            }
            else if (token.startsWith(NO_CACHE)) {
                cacheControl.setNoCache(true);
                addFields(cacheControl.getNoCacheFields(), token);
            }
            else {
                List<String> pair = ImmutableList.copyOf(Splitter.on("=").limit(2).split(token));
                if (pair.size() == 2) {
                    cacheControl.getCacheExtension().put(pair.get(0), pair.get(1));
                }
                else {
                    cacheControl.getCacheExtension().put(pair.get(0), "");
                }
            }
        }

        return cacheControl;
    }

    public String toString()
    {
        StringBuilder buffer = new StringBuilder();
        if (isPrivate()) {
            buffer.append(PRIVATE);
            handleFields(getPrivateFields(), buffer);
            buffer.append(SEPARATOR);
        }
        if (isNoCache()) {
            buffer.append(NO_CACHE);
            handleFields(getNoCacheFields(), buffer);
            buffer.append(SEPARATOR);
        }
        if (isNoStore()) {
            buffer.append(NO_STORE).append(SEPARATOR);
        }
        if (isNoTransform()) {
            buffer.append(NO_TRANSFORM).append(SEPARATOR);
        }
        if (isMustRevalidate()) {
            buffer.append(MUST_REVALIDATE).append(SEPARATOR);
        }
        if (isProxyRevalidate()) {
            buffer.append(PROXY_REVALIDATE).append(SEPARATOR);
        }
        if (getMaxAge() != -1) {
            buffer.append(MAX_AGE).append('=').append(getMaxAge()).append(SEPARATOR);
        }
        if (getSMaxAge() != -1) {
            buffer.append(SMAX_AGE).append('=').append(getSMaxAge()).append(SEPARATOR);
        }

        Map<String, String> extension = getCacheExtension();
        for (Map.Entry<String, String> entry : extension.entrySet()) {
            buffer.append(entry.getKey());
            String value = entry.getValue();
            if (value != null) {
                buffer.append("=");
                if (value.indexOf(' ') != -1) {
                    buffer.append('\"').append(value).append('\"');
                }
                else {
                    buffer.append(value);
                }
            }
            buffer.append(SEPARATOR);
        }

        String string = buffer.toString();
        if (string.endsWith(SEPARATOR)) {
            string = string.substring(0, string.length() - 1);
        }
        return string;
    }

    private static void handleFields(List<String> fields, StringBuilder buffer) {
        if (fields.isEmpty()) {
            return;
        }
        buffer.append('=');
        buffer.append('\"');
        for (Iterator<String> it = fields.iterator(); it.hasNext();) {
            buffer.append(it.next());
            if (it.hasNext()) {
                buffer.append(',');
            }
        }
        buffer.append('\"');
    }

    private static List<String> getTokens(String string)
    {
        if (string.contains("\"")) {
            List<String> values = new ArrayList<>(4);
            Matcher m = COMPLEX_HEADER_PATTERN.matcher(string);
            while (m.find()) {
                String val = m.group().trim();
                if (val.length() > 0) {
                    values.add(val);
                }
            }
            return ImmutableList.copyOf(values);
        }
        else {
            return ImmutableList.copyOf(Splitter.on(SEPARATOR).split(string));
        }
    }

    private static void addFields(List<String> fields, String token)
    {
        int i = token.indexOf('=');
        if (i != -1) {
            String f = i == token.length() + 1 ? "" : token.substring(i + 1);
            if (f.length() >= 2 && f.startsWith("\"") && f.endsWith("\"")) {
                f = f.length() == 2 ? "" : f.substring(1, f.length() - 1);
                if (f.length() > 0) {
                    List<String> values = ImmutableList.copyOf(Splitter.on(",").split(f));
                    for (String v : values) {
                        fields.add(v.trim());
                    }
                }
            }
        }
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(maxAge,
                sMaxAge,
                isPrivate,
                noCache,
                noStore,
                noTransform,
                mustRevalidate,
                proxyRevalidate,
                cacheExtensions,
                noCacheFields,
                privateFields);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        CacheControl other = (CacheControl) obj;
        return Objects.equal(this.maxAge, other.maxAge) &&
                Objects.equal(this.sMaxAge, other.sMaxAge) &&
                Objects.equal(this.isPrivate, other.isPrivate) &&
                Objects.equal(this.noCache, other.noCache) &&
                Objects.equal(this.noStore, other.noStore) &&
                Objects.equal(this.noTransform, other.noTransform) &&
                Objects.equal(this.mustRevalidate, other.mustRevalidate) &&
                Objects.equal(this.proxyRevalidate, other.proxyRevalidate) &&
                Objects.equal(this.cacheExtensions, other.cacheExtensions) &&
                Objects.equal(this.noCacheFields, other.noCacheFields) &&
                Objects.equal(this.privateFields, other.privateFields);
    }
}
