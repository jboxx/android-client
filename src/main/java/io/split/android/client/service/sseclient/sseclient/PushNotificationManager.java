package io.split.android.client.service.sseclient.sseclient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.split.android.client.service.executor.SplitTask;
import io.split.android.client.service.executor.SplitTaskExecutionInfo;
import io.split.android.client.service.executor.SplitTaskType;
import io.split.android.client.service.sseclient.SseJwtToken;
import io.split.android.client.service.sseclient.feedbackchannel.PushManagerEventBroadcaster;
import io.split.android.client.service.sseclient.feedbackchannel.PushStatusEvent;
import io.split.android.client.service.sseclient.feedbackchannel.PushStatusEvent.EventType;
import io.split.android.client.utils.Logger;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.reflect.Modifier.PRIVATE;

public class PushNotificationManager {

    private final static int POOL_SIZE = 1;
    private final static long AWAIT_SHUTDOWN_TIME = 5;
    private final ScheduledExecutorService mExecutor;
    private final PushManagerEventBroadcaster mBroadcasterChannel;
    private final SseAuthenticator mSseAuthenticator;
    private final SseClient mSseClient;
    private SseRefreshTokenTimer mRefreshTokenTimer;
    private SseDisconnectionTimer mDisconnectionTimer;

    private AtomicBoolean mIsPaused;
    private AtomicBoolean mIsStopped;

    @VisibleForTesting(otherwise = PRIVATE)
    public PushNotificationManager(@NonNull PushManagerEventBroadcaster broadcasterChannel,
                                   @NonNull SseAuthenticator sseAuthenticator,
                                   @NonNull SseClient sseClient,
                                   @NonNull SseRefreshTokenTimer refreshTokenTimer,
                                   @NonNull SseDisconnectionTimer disconnectionTimer,
                                   @Nullable ScheduledExecutorService executor) {
        mBroadcasterChannel = checkNotNull(broadcasterChannel);
        mSseAuthenticator = checkNotNull(sseAuthenticator);
        mSseClient = checkNotNull(sseClient);
        mRefreshTokenTimer = checkNotNull(refreshTokenTimer);
        mDisconnectionTimer = checkNotNull(disconnectionTimer);
        mIsStopped = new AtomicBoolean(false);
        mIsPaused = new AtomicBoolean(false);

        if(executor != null) {
            mExecutor = executor;
        } else {
            mExecutor = buildExecutor();
        }
    }

    public void start() {
        Logger.d("Push notification manager started");
        connect();
    }

    public void pause() {
        Logger.d("Push notification manager paused");
        mIsPaused.set(true);
        mDisconnectionTimer.schedule(new SplitTask() {
            @NonNull
            @Override
            public SplitTaskExecutionInfo execute() {
                Logger.d("Disconnecting streaming while in background");
                mSseClient.disconnect();
                mRefreshTokenTimer.cancel();
                return SplitTaskExecutionInfo.success(SplitTaskType.GENERIC_TASK);
            }
        });
    }

    public void resume() {
        Logger.d("Push notification manager resumed");
        mIsPaused.set(false);
        mDisconnectionTimer.cancel();
        if(mSseClient.status() == SseClient.DISCONNECTED && !mIsStopped.get()) {
            connect();
        }
    }

    public void stop() {
        Logger.d("Shutting down SSE client");
        mIsStopped.set(true);
        mDisconnectionTimer.cancel();
        mRefreshTokenTimer.cancel();
        mSseClient.disconnect();
        shutdownAndAwaitTermination();
    }
    private void connect() {
        if(mSseClient.status() == SseClient.CONNECTED) {
            mSseClient.disconnect();
        }
        mExecutor.submit(new StreamingConnection());
    }

    private void shutdownAndAwaitTermination() {
        mExecutor.shutdown();
        try {
            if (!mExecutor.awaitTermination(AWAIT_SHUTDOWN_TIME, TimeUnit.SECONDS)) {
                mExecutor.shutdownNow();
                if (!mExecutor.awaitTermination(AWAIT_SHUTDOWN_TIME, TimeUnit.SECONDS))
                    System.err.println("Sse client pool did not terminate");
            }
        } catch (InterruptedException ie) {
            mExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private ScheduledThreadPoolExecutor buildExecutor() {
        ThreadFactoryBuilder threadFactoryBuilder = new ThreadFactoryBuilder();
        threadFactoryBuilder.setDaemon(true);
        threadFactoryBuilder.setNameFormat("split-sse_client-%d");
        threadFactoryBuilder.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                Logger.e(e, "Error in thread: %s", t.getName());
            }
        });

        return new ScheduledThreadPoolExecutor(POOL_SIZE, threadFactoryBuilder.build());
    }


    private class StreamingConnection implements Runnable {

        @Override
        public void run() {

            SseAuthenticationResult authResult = mSseAuthenticator.authenticate();

            if(authResult.isSuccess() && !authResult.isPushEnabled()) {
                Logger.d("Streaming disabled for api key");
                mBroadcasterChannel.pushMessage(new PushStatusEvent(EventType.PUSH_SUBSYSTEM_DOWN));
                mIsStopped.set(true);
                return;
            }

            if(!authResult.isSuccess() && !authResult.isErrorRecoverable()) {
                Logger.d("Streaming no recoverable auth error.");
                mBroadcasterChannel.pushMessage(new PushStatusEvent(EventType.PUSH_NON_RETRYABLE_ERROR));
                mIsStopped.set(true);
                return;
            }

            if( !authResult.isSuccess() && authResult.isErrorRecoverable()) {
                mBroadcasterChannel.pushMessage(new PushStatusEvent(EventType.PUSH_RETRYABLE_ERROR));
                return;
            }

            SseJwtToken token = authResult.getJwtToken();
            if(token == null || token.getChannels() == null || token.getRawJwt() == null) {
                Logger.d("Streaming auth error. Retrying");
                mBroadcasterChannel.pushMessage(new PushStatusEvent(EventType.PUSH_RETRYABLE_ERROR));
                return;
            }

            // If host app is in bg or push manager stopped, abort the process
            if(mIsPaused.get() || mIsStopped.get()) {
                return;
            }

            mSseClient.connect(token, new SseClientImpl.ConnectionListener() {
                @Override
                public void onConnectionSuccess() {
                    mBroadcasterChannel.pushMessage(new PushStatusEvent(EventType.PUSH_SUBSYSTEM_UP));
                    mRefreshTokenTimer.schedule(token.getIssuedAtTime(), token.getExpirationTime());
                }
            });
        }

        private void logError(String message, Exception e) {
            Logger.e(message + " : " + e.getLocalizedMessage());
        }
    }
}
