/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.jmx.http.rpc;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import javax.management.remote.JMXAddressable;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;
import javax.security.auth.Subject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.google.common.base.MoreObjects.toStringHelper;
import static io.airlift.jmx.http.rpc.HttpMBeanServerRpc.propagateIfInstanceOf;
import static io.airlift.jmx.http.rpc.HttpMBeanServerRpc.propagateIfPossible;
import static java.lang.String.format;

public class HttpJmxConnector implements JMXConnector, JMXAddressable
{
    private final JMXServiceURL jmxServiceUrl;
    private final URI baseUri;
    private final String connectionId = "http-" + UUID.randomUUID();
    private final HttpMBeanServerCredentials credentials;

    public HttpJmxConnector(JMXServiceURL jmxServiceUrl, Map<String, ?> environment)
            throws MalformedURLException
    {
        String[] credentials = (String[]) environment.get(JMXConnector.CREDENTIALS);
        if (credentials != null) {
            this.credentials = new HttpMBeanServerCredentials(credentials[0], credentials[1]);
        }
        else {
            this.credentials = null;
        }

        // only http and https URLs are allowed
        String protocol = jmxServiceUrl.getProtocol();
        if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
            throw new MalformedURLException(jmxServiceUrl.toString());
        }

        // path
        String urlPath = jmxServiceUrl.getURLPath();
        if (!urlPath.endsWith("/")) {
            urlPath += "/";
        }
        urlPath += "v1/jmx/mbeanServer/";

        // port
        int port = jmxServiceUrl.getPort();
        if (port == 0) {
            if ("http".equalsIgnoreCase(protocol)) {
                port = 80;
            }
            else {
                port = 433;
            }
        }

        try {
            this.baseUri = new URI(protocol, null, jmxServiceUrl.getHost(), port, urlPath, null, null);
        }
        catch (URISyntaxException e) {
            throw new MalformedURLException(jmxServiceUrl.toString());
        }

        this.jmxServiceUrl = jmxServiceUrl;
    }

    @Override
    public JMXServiceURL getAddress()
    {
        return jmxServiceUrl;
    }

    @Override
    public String getConnectionId()
    {
        return connectionId;
    }

    @Override
    public void connect()
    {
    }

    @Override
    public void connect(Map<String, ?> env)
    {
    }

    @Override
    public MBeanServerConnection getMBeanServerConnection()
    {
        return new HttpMBeanServerConnection(baseUri, credentials);
    }

    @Override
    public MBeanServerConnection getMBeanServerConnection(Subject delegationSubject)
    {
        return getMBeanServerConnection();
    }

    @Override
    public void close()
    {
    }

    @Override
    public void addConnectionNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback)
    {
    }

    @Override
    public void removeConnectionNotificationListener(NotificationListener listener)
    {
    }

    @Override
    public void removeConnectionNotificationListener(NotificationListener l, NotificationFilter f, Object handback)
    {
    }

    public static class HttpMBeanServerConnection implements MBeanServerConnection
    {
        private final URI baseUri;
        private final HttpMBeanServerCredentials credentials;

        public HttpMBeanServerConnection(URI baseUri, HttpMBeanServerCredentials credentials)
        {
            this.baseUri = baseUri;
            this.credentials = credentials;
        }

        private Object invoke(String method, Object... args)
                throws Exception
        {
            OutputStream outputStream = null;
            InputStream inputStream = null;
            try {
                HttpURLConnection urlConnection = (HttpURLConnection) baseUri.resolve(method).toURL().openConnection(Proxy.NO_PROXY);
                urlConnection.setRequestMethod("GET");
                if (credentials != null) {
                    urlConnection.setRequestProperty("Authorization", credentials.toBasicAuthHeader());
                }

                urlConnection.setDoOutput(true);
                urlConnection.setChunkedStreamingMode(4096);
                outputStream = urlConnection.getOutputStream();
                outputStream.write(HttpMBeanServerRpc.serialize(args));
                outputStream.close();

                // stupid URL client just throws away response when response is 401
                int statusCode = urlConnection.getResponseCode();
                if (statusCode == 401) {
                    throw new SecurityException("Unauthorized");
                }

                // get correct response stream (Yes, java.net.URL is dumb)
                if (statusCode < 400) {
                    inputStream = urlConnection.getInputStream();
                }
                else {
                    inputStream = urlConnection.getErrorStream();
                }

                // deserialize response
                Object result = HttpMBeanServerRpc.deserialize(inputStream);

                // any non-200 response must contain a serialized exception
                if (statusCode / 100 != 2) {
                    if (result instanceof Exception) {
                        throw (Exception) result;
                    }
                    throw new IllegalStateException(format("Expected response (%d) body to contain a serialized Exception, but body contains a serialized %s",
                            statusCode,
                            result.getClass().getName()));
                }
                return result;
            }
            finally {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    }
                    catch (IOException ignored) {
                    }
                }
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    }
                    catch (IOException ignored) {
                    }
                }
            }
        }

        @Override
        public ObjectInstance getObjectInstance(ObjectName name)
                throws InstanceNotFoundException, IOException
        {
            try {
                return (ObjectInstance) invoke("getObjectInstance", name);
            }
            catch (Exception e) {
                propagateIfInstanceOf(e, InstanceNotFoundException.class);
                propagateIfInstanceOf(e, IOException.class);
                propagateIfPossible(e);
                throw new IOException(e);
            }
        }

        @Override
        public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query)
                throws IOException
        {
            try {
                return (Set<ObjectInstance>) invoke("queryMBeans", name, query);
            }
            catch (Exception e) {
                propagateIfInstanceOf(e, IOException.class);
                propagateIfPossible(e);
                throw new IOException(e);
            }
        }

        @Override
        public Set<ObjectName> queryNames(ObjectName name, QueryExp query)
                throws IOException
        {
            try {
                return (Set<ObjectName>) invoke("queryNames", name, query);
            }
            catch (Exception e) {
                propagateIfInstanceOf(e, IOException.class);
                propagateIfPossible(e);
                throw new IOException(e);
            }

        }

        @Override
        public boolean isRegistered(ObjectName name)
                throws IOException
        {
            try {
                return (Boolean) invoke("isRegistered", name);
            }
            catch (Exception e) {
                propagateIfInstanceOf(e, IOException.class);
                propagateIfPossible(e);
                throw new IOException(e);
            }
        }

        @Override
        public Integer getMBeanCount()
                throws IOException
        {
            try {
                return (Integer) invoke("getMBeanCount");
            }
            catch (Exception e) {
                propagateIfInstanceOf(e, IOException.class);
                propagateIfPossible(e);
                throw new IOException(e);
            }
        }

        @Override
        public Object getAttribute(ObjectName name, String attribute)
                throws MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException, IOException
        {
            try {
                return invoke("getAttribute", name, attribute);
            }
            catch (Exception e) {
                propagateIfInstanceOf(e, MBeanException.class);
                propagateIfInstanceOf(e, AttributeNotFoundException.class);
                propagateIfInstanceOf(e, InstanceNotFoundException.class);
                propagateIfInstanceOf(e, ReflectionException.class);
                propagateIfInstanceOf(e, IOException.class);
                propagateIfPossible(e);
                throw new IOException(e);
            }
        }

        @Override
        public AttributeList getAttributes(ObjectName name, String[] attributes)
                throws InstanceNotFoundException, ReflectionException, IOException
        {
            try {
                return (AttributeList) invoke("getAttributes", name, attributes);
            }
            catch (Exception e) {
                propagateIfInstanceOf(e, InstanceNotFoundException.class);
                propagateIfInstanceOf(e, ReflectionException.class);
                propagateIfInstanceOf(e, IOException.class);
                propagateIfPossible(e);
                throw new IOException(e);
            }
        }

        @Override
        public void setAttribute(ObjectName name, Attribute attribute)
                throws InstanceNotFoundException, AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException, IOException
        {
            try {
                invoke("setAttribute", name, attribute);
            }
            catch (Exception e) {
                propagateIfInstanceOf(e, InstanceNotFoundException.class);
                propagateIfInstanceOf(e, AttributeNotFoundException.class);
                propagateIfInstanceOf(e, InvalidAttributeValueException.class);
                propagateIfInstanceOf(e, MBeanException.class);
                propagateIfInstanceOf(e, ReflectionException.class);
                propagateIfInstanceOf(e, IOException.class);
                propagateIfPossible(e);
                throw new IOException(e);
            }
        }

        @Override
        public AttributeList setAttributes(ObjectName name, AttributeList attributes)
                throws InstanceNotFoundException, ReflectionException, IOException
        {
            try {
                return (AttributeList) invoke("setAttributes", name, attributes);
            }
            catch (Exception e) {
                propagateIfInstanceOf(e, InstanceNotFoundException.class);
                propagateIfInstanceOf(e, ReflectionException.class);
                propagateIfInstanceOf(e, IOException.class);
                propagateIfPossible(e);
                throw new IOException(e);
            }
        }

        @Override
        public Object invoke(ObjectName name, String operationName, Object[] params, String[] signature)
                throws InstanceNotFoundException, MBeanException, ReflectionException, IOException
        {
            try {
                return invoke("invoke", name, operationName, params, signature);
            }
            catch (Exception e) {
                propagateIfInstanceOf(e, InstanceNotFoundException.class);
                propagateIfInstanceOf(e, MBeanException.class);
                propagateIfInstanceOf(e, ReflectionException.class);
                propagateIfInstanceOf(e, IOException.class);
                propagateIfPossible(e);
                throw new IOException(e);
            }
        }

        @Override
        public String getDefaultDomain()
                throws IOException
        {
            try {
                return (String) invoke("getDefaultDomain");
            }
            catch (Exception e) {
                propagateIfInstanceOf(e, IOException.class);
                propagateIfPossible(e);
                throw new IOException(e);
            }
        }

        @Override
        public String[] getDomains()
                throws IOException
        {
            try {
                return (String[]) invoke("getDomains");
            }
            catch (Exception e) {
                propagateIfInstanceOf(e, IOException.class);
                propagateIfPossible(e);
                throw new IOException(e);
            }
        }

        @Override
        public MBeanInfo getMBeanInfo(ObjectName name)
                throws InstanceNotFoundException, IntrospectionException, ReflectionException, IOException
        {
            try {
                return (MBeanInfo) invoke("getMBeanInfo", name);
            }
            catch (Exception e) {
                propagateIfInstanceOf(e, InstanceNotFoundException.class);
                propagateIfInstanceOf(e, IntrospectionException.class);
                propagateIfInstanceOf(e, ReflectionException.class);
                propagateIfInstanceOf(e, IOException.class);
                propagateIfPossible(e);
                throw new IOException(e);
            }
        }

        @Override
        public boolean isInstanceOf(ObjectName name, String className)
                throws InstanceNotFoundException, IOException
        {
            try {
                return (Boolean) invoke("isInstanceOf", name, className);
            }
            catch (Exception e) {
                propagateIfInstanceOf(e, InstanceNotFoundException.class);
                propagateIfInstanceOf(e, IOException.class);
                propagateIfPossible(e);
                throw new IOException(e);
            }
        }

        @Override
        public String toString()
        {
            return toStringHelper(this)
                    .add("baseUri", baseUri)
                    .toString();
        }

        //
        // Unsupported
        //


        @Override
        public ObjectInstance createMBean(String className, ObjectName name)
        {
            new Exception().printStackTrace();
            throw new UnsupportedOperationException();
        }

        @Override
        public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName)
        {
            new Exception().printStackTrace();
            throw new UnsupportedOperationException();
        }

        @Override
        public ObjectInstance createMBean(String className, ObjectName name, Object[] params, String[] signature)
        {
            new Exception().printStackTrace();
            throw new UnsupportedOperationException();
        }

        @Override
        public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName, Object[] params, String[] signature)
        {
            new Exception().printStackTrace();
            throw new UnsupportedOperationException();
        }

        @Override
        public void unregisterMBean(ObjectName name)
        {
            new Exception().printStackTrace();
            throw new UnsupportedOperationException();
        }

        @Override
        public void addNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter, Object handback)
        {
        }

        @Override
        public void addNotificationListener(ObjectName name, ObjectName listener, NotificationFilter filter, Object handback)
        {
        }

        @Override
        public void removeNotificationListener(ObjectName name, ObjectName listener)
        {
        }

        @Override
        public void removeNotificationListener(ObjectName name, ObjectName listener, NotificationFilter filter, Object handback)
        {
        }

        @Override
        public void removeNotificationListener(ObjectName name, NotificationListener listener)
        {
        }

        @Override
        public void removeNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter, Object handback)
        {
        }
    }
}
