package io.split.android.client.service.sseclient;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.split.android.client.network.HttpClient;
import io.split.android.client.network.HttpException;
import io.split.android.client.network.HttpStreamRequest;
import io.split.android.client.network.HttpStreamResponse;
import io.split.android.client.network.URIBuilder;
import io.split.android.client.utils.Logger;
import io.split.android.client.utils.StringHelper;

import static androidx.core.util.Preconditions.checkNotNull;
import static java.lang.reflect.Modifier.PRIVATE;

public class SseClient {

    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String CONTENT_TYPE_VALUE_STREAM = "text/event-stream";
    private final static int POOL_SIZE = 4;
    private final static long AWAIT_SHUTDOWN_TIME = 60;
    private final URI mTargetUrl;
    private AtomicInteger mReadyState;
    private final HttpClient mHttpClient;
    private HttpStreamRequest mHttpStreamRequest = null;
    private EventStreamParser mEventStreamParser;
    private WeakReference<SseClientListener> mListener;
    private final ScheduledExecutorService mExecutor;
    private ScheduledFuture mDisconnectionTimerTaskRef = null;
    private AtomicBoolean isDisconnectCalled;
    private Future mConnectionTask;

    final static int CONNECTING = 0;
    final static int CLOSED = 2;
    final static int OPEN = 1;

    public SseClient(@NonNull URI uri,
                     @NonNull HttpClient httpClient,
                     @NonNull EventStreamParser eventStreamParser) {
        this(uri, httpClient, eventStreamParser, new ScheduledThreadPoolExecutor(POOL_SIZE));
    }

    @VisibleForTesting(otherwise = PRIVATE)
    public SseClient(@NonNull URI uri,
                     @NonNull HttpClient httpClient,
                     @NonNull EventStreamParser eventStreamParser,
                     @NonNull ScheduledExecutorService executor) {
        mTargetUrl = checkNotNull(uri);
        mHttpClient = checkNotNull(httpClient);
        mEventStreamParser = checkNotNull(eventStreamParser);
        mReadyState = new AtomicInteger(CLOSED);
        isDisconnectCalled = new AtomicBoolean(false);
        mExecutor = checkNotNull(executor);
        mReadyState.set(CLOSED);
    }

    public int readyState() {
        return mReadyState.get();
    }

    public void connect(String token, List<String> channels) {
        disconnect();
        mReadyState.set(CONNECTING);
        mConnectionTask = mExecutor.submit(new PersistentConnectionExecutor(token, channels));
    }

    public void disconnect() {
        if (readyState() == OPEN) {
            isDisconnectCalled.set(true);
            setCloseStatus();
            triggerOnDisconnect();
            mHttpStreamRequest.close();
            if(mConnectionTask != null) {
                mConnectionTask.cancel(true);
            }
        }
    }

    public void setListener(SseClientListener listener) {
        mListener = new WeakReference<>(listener);
    }

    public void close() {
        disconnect();
        shutdownAndAwaitTermination();
    }

    private void shutdownAndAwaitTermination() {
        mExecutor.shutdown();
        try {
            if (!mExecutor.awaitTermination(AWAIT_SHUTDOWN_TIME, TimeUnit.SECONDS)) {
                mExecutor.shutdownNow();
                if (!mExecutor.awaitTermination(AWAIT_SHUTDOWN_TIME, TimeUnit.SECONDS))
                    System.err.println("Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            mExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void setCloseStatus() {
        mReadyState.set(CLOSED);
    }

    private void triggerOnMessage(Map<String, String> messageValues) {
        if (mListener == null) {
            return;
        }
        SseClientListener listener = mListener.get();
        if (listener != null) {
            listener.onMessage(messageValues);
        }
    }

    private void triggerOnKeepAlive() {
        SseClientListener listener = mListener.get();
        if (listener != null) {
            listener.onKeepAlive();
        }
    }

    private void triggerOnError(boolean isRecoverable) {
        SseClientListener listener = mListener.get();
        if (listener != null) {
            listener.onError(isRecoverable);
        }
    }

    private void triggerOnDisconnect() {
        SseClientListener listener = mListener.get();
        if (listener != null) {
            listener.onDisconnect();
        }
    }

    private void triggerOnOpen() {
        SseClientListener listener = mListener.get();
        if (listener != null) {
            listener.onOpen();
        }
    }

    private class PersistentConnectionExecutor implements Runnable {
        private static final String PUSH_NOTIFICATION_CHANNELS_PARAM = "channel";
        private static final String PUSH_NOTIFICATION_TOKEN_PARAM = "accessToken";
        private static final String PUSH_NOTIFICATION_VERSION_PARAM = "v";
        private static final String PUSH_NOTIFICATION_VERSION_VALUE = "1.1";

        private final StringHelper mStringHelper;
        private final List<String> mChannels;
        private final String mToken;
        private BufferedReader mBufferedReader;

        public PersistentConnectionExecutor(@NonNull String token,
                                            @NonNull List<String> channels) {
            mToken = checkNotNull(token);
            mChannels = checkNotNull(channels);
            mStringHelper = new StringHelper();
        }

        @Override
        public void run() {
            String channels = mStringHelper.join(",", mChannels);
            mBufferedReader = null;
            try {
                URI url = new URIBuilder(mTargetUrl)
                        .addParameter(PUSH_NOTIFICATION_VERSION_PARAM, PUSH_NOTIFICATION_VERSION_VALUE)
                        .addParameter(PUSH_NOTIFICATION_CHANNELS_PARAM, channels)
                        .addParameter(PUSH_NOTIFICATION_TOKEN_PARAM, mToken)
                        .build();
                mHttpStreamRequest = mHttpClient.streamRequest(url);
                mHttpStreamRequest.addHeader(CONTENT_TYPE_HEADER, CONTENT_TYPE_VALUE_STREAM);
                HttpStreamResponse response = mHttpStreamRequest.execute();
                if (response.isSuccess()) {
                    mBufferedReader = response.getBufferedReader();
                    if (mBufferedReader != null) {
                        Logger.i("Streaming connection opened");
                        triggerOnOpen();
                        mReadyState.set(OPEN);

                        String inputLine;
                        Map<String, String> values = new HashMap<>();
                        while ((inputLine = mBufferedReader.readLine()) != null) {
                            if (mEventStreamParser.parseLineAndAppendValue(inputLine, values)) {
                                if (mEventStreamParser.isKeepAlive(values)) {
                                    triggerOnKeepAlive();
                                } else {
                                    triggerOnMessage(values);
                                }
                                values = new HashMap<>();
                            }
                        }
                    } else {
                        throw (new IOException("Buffer is null"));
                    }
                } else {
                    Logger.e("Streaming connection error. Http return code "
                            + response.getHttpStatus());
                    triggerOnError(!response.isCredentialsError());
                }
            } catch (URISyntaxException e) {
                Logger.e("An error has ocurred while creating stream Url " +
                        mTargetUrl.toString() + " : " + e.getLocalizedMessage());
                triggerOnError(true);
            } catch (HttpException e) {
                Logger.e("Unexpected error has ocurred while trying to connecting to stream " +
                        mTargetUrl.toString() + " : " + e.getLocalizedMessage());
                triggerOnError(true);
            } catch (IOException e) {
                if (!isDisconnectCalled.getAndSet(false)) {
                    Logger.e("An error has ocurred while parsing stream from " +
                            mTargetUrl.toString() + " : " + e.getLocalizedMessage());
                    triggerOnError(true);
                }
            } catch (Exception e) {
                Logger.e("An unexpected error has ocurred while receiving stream events from " +
                        mTargetUrl.toString() + " : " + e.getLocalizedMessage());
                triggerOnError(true);
            } finally {
                if (mBufferedReader != null) {
                    try {
                        mBufferedReader.close();
                    } catch (IOException e) {
                        Logger.w("Error closing streaming buffer");
                    }
                }
                setCloseStatus();
            }
        }
    }

    public void scheduleDisconnection
            (long delayInSecods) {
        Logger.d(String.format("Streaming will be disconnected in %d seconds", delayInSecods));
        mDisconnectionTimerTaskRef = mExecutor.schedule(new DisconnectionTimer(), delayInSecods, TimeUnit.SECONDS);
    }

    public boolean cancelDisconnectionTimer() {
        boolean taskWasCancelled = false;
        if (mDisconnectionTimerTaskRef != null) {
            mDisconnectionTimerTaskRef.cancel(false);
            taskWasCancelled = mDisconnectionTimerTaskRef.isCancelled();
            mDisconnectionTimerTaskRef = null;
        }
        return taskWasCancelled;
    }

    private class DisconnectionTimer implements Runnable {
        @Override
        public void run() {
            Logger.d("Disconnecting while in background");
            disconnect();
        }
    }
}
