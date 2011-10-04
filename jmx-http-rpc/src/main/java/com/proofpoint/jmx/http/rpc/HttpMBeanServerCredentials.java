package com.proofpoint.jmx.http.rpc;

public class HttpMBeanServerCredentials
{
    private final String username;
    private final String password;

    public HttpMBeanServerCredentials(String username, String password)
    {
        this.username = username;
        this.password = password;
    }

    public boolean authenticate(HttpMBeanServerCredentials userCredentials)
    {
        if (username == null && password == null) {
            return true;
        }

        return equals(username, userCredentials.username) &&
                equals(password, userCredentials.password);
    }

    public static HttpMBeanServerCredentials fromBasicAuthHeader(String authHeader)
    {
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return new HttpMBeanServerCredentials(null, null);
        }

        String credentials = new String(HttpMBeanServerRpc.base64Decode(authHeader.substring("Basic ".length())));
        int index = credentials.indexOf(':');
        if (index >= 0) {
            return new HttpMBeanServerCredentials(credentials.substring(0, index), credentials.substring(index + 1));
        }
        else {
            return new HttpMBeanServerCredentials(credentials, null);
        }
    }

    public String toBasicAuthHeader()
    {
        return "Basic " + HttpMBeanServerRpc.base64Encode(String.format("%s:%s", username == null ? "" : username, password == null ? "" : password));
    }

    private static boolean equals(Object left, Object right)
    {
        if (left == null) {
            return right == null;
        }
        else {
            return left.equals(right);
        }
    }
}
