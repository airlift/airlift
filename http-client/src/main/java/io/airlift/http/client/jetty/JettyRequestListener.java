package io.airlift.http.client.jetty;

import java.net.URI;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;

class JettyRequestListener implements Request.Listener, Response.Listener {
    enum State {
        CREATED,
        SENDING_REQUEST,
        AWAITING_RESPONSE,
        READING_RESPONSE,
        FINISHED
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.CREATED);

    private final URI uri;
    private final long created = System.nanoTime();
    private final AtomicLong requestStarted = new AtomicLong();
    private final AtomicLong requestFinished = new AtomicLong();
    private final AtomicLong responseStarted = new AtomicLong();
    private final AtomicLong responseFinished = new AtomicLong();

    public JettyRequestListener(URI uri) {
        this.uri = uri;
    }

    public URI getUri() {
        return uri;
    }

    public State getState() {
        return state.get();
    }

    public long getCreated() {
        return created;
    }

    public long getRequestStarted() {
        return requestStarted.get();
    }

    public long getRequestFinished() {
        return requestFinished.get();
    }

    public long getResponseStarted() {
        return responseStarted.get();
    }

    public long getResponseFinished() {
        return responseFinished.get();
    }

    @Override
    public void onBegin(Request request) {
        changeState(State.SENDING_REQUEST);

        long now = System.nanoTime();
        requestStarted.compareAndSet(0, now);
    }

    @Override
    public void onSuccess(Request request) {
        changeState(State.AWAITING_RESPONSE);

        long now = System.nanoTime();
        requestStarted.compareAndSet(0, now);
        requestFinished.compareAndSet(0, now);
    }

    @Override
    public void onBegin(Response response) {
        changeState(State.READING_RESPONSE);

        long now = System.nanoTime();
        requestStarted.compareAndSet(0, now);
        requestFinished.compareAndSet(0, now);
        responseStarted.compareAndSet(0, now);
    }

    @Override
    public void onComplete(Result result) {
        changeState(State.FINISHED);

        long now = System.nanoTime();
        requestStarted.compareAndSet(0, now);
        requestFinished.compareAndSet(0, now);
        responseStarted.compareAndSet(0, now);
        responseFinished.compareAndSet(0, now);
    }

    private synchronized void changeState(State newState) {
        if (state.get().ordinal() < newState.ordinal()) {
            state.set(newState);
        }
    }
}
