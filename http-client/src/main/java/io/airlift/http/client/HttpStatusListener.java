package io.airlift.http.client;

public interface HttpStatusListener {
    void statusReceived(int statusCode);
}
