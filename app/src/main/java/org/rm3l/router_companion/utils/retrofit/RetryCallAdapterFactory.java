package org.rm3l.router_companion.utils.retrofit;

import androidx.annotation.NonNull;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Request;
import org.rm3l.router_companion.exceptions.TimeoutError;
import retrofit2.Call;
import retrofit2.CallAdapter;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

/**
 * Retries calls marked with {@link Retry}.
 */
public class RetryCallAdapterFactory extends CallAdapter.Factory {

    private static final class RetryingCall<T> implements Call<T> {

        private final AtomicBoolean isCanceled;

        private final AtomicBoolean isExecuted;

        private final Call<T> mDelegate;

        private final ScheduledExecutorService mExecutor;

        private final int mMaxRetries;

        RetryingCall(Call<T> delegate, ScheduledExecutorService executor, int maxRetries) {
            mDelegate = delegate;
            mExecutor = executor;
            mMaxRetries = maxRetries;
            isExecuted = new AtomicBoolean(false);
            isCanceled = new AtomicBoolean(false);
        }

        @Override
        public void cancel() {
            mDelegate.cancel();
            isCanceled.set(true);
        }

        @SuppressWarnings("CloneDoesntCallSuperClone" /* Performing deep clone */)
        @Override
        public Call<T> clone() {
            return new RetryingCall<>(mDelegate.clone(), mExecutor, mMaxRetries);
        }

        @Override
        public void enqueue(@NonNull Callback<T> callback) {
            mDelegate.enqueue(new RetryingCallback<>(mDelegate, callback, mExecutor, mMaxRetries));
        }

        @Override
        public Response<T> execute() throws IOException {
            isExecuted.set(true);
            return mDelegate.execute();
        }

        @Override
        public boolean isCanceled() {
            return isCanceled.get();
        }

        @Override
        public boolean isExecuted() {
            return isExecuted.get();
        }

        @Override
        public Request request() {
            return null;
        }
    }

    // Exponential backoff approach from https://developers.google.com/drive/web/handle-errors
    private static final class RetryingCallback<T> implements Callback<T> {

        private static Random random = new Random();

        private final Call<T> mCall;

        private final Callback<T> mDelegate;

        private final ScheduledExecutorService mExecutor;

        private final int mMaxRetries;

        private final int mRetries;

        RetryingCallback(Call<T> call, Callback<T> delegate, ScheduledExecutorService executor,
                int maxRetries) {
            this(call, delegate, executor, maxRetries, 0);
        }

        RetryingCallback(Call<T> call, Callback<T> delegate, ScheduledExecutorService executor,
                int maxRetries, int retries) {
            mCall = call;
            mDelegate = delegate;
            mExecutor = executor;
            mMaxRetries = maxRetries;
            mRetries = retries;
        }

        @Override
        public void onFailure(@NonNull Call<T> call, @NonNull Throwable throwable) {
            // Retry failed request
            if (mRetries < mMaxRetries) {
                retryCall();
            } else {
                mDelegate.onFailure(call, new TimeoutError(throwable));
            }
        }

        @Override
        public void onResponse(@NonNull Call<T> call, @NonNull Response<T> response) {
            mDelegate.onResponse(call, response);
        }

        private void retryCall() {
            mExecutor.schedule(new Runnable() {
                @Override
                public void run() {
                    final Call<T> call = mCall.clone();
                    call.enqueue(
                            new RetryingCallback<>(call, mDelegate, mExecutor, mMaxRetries, mRetries + 1));
                }
            }, (1 << mRetries) * 1000 + random.nextInt(1001), TimeUnit.MILLISECONDS);
        }
    }

    private final ScheduledExecutorService mExecutor;

    public static RetryCallAdapterFactory create() {
        return new RetryCallAdapterFactory();
    }

    private RetryCallAdapterFactory() {
        mExecutor = Executors.newScheduledThreadPool(1);
    }

    @Override
    public CallAdapter<?, ?> get(@NonNull final Type returnType,
            @NonNull Annotation[] annotations,
            @NonNull Retrofit retrofit) {
        boolean hasRetryAnnotation = false;
        int value = 0;
        for (Annotation annotation : annotations) {
            if (annotation instanceof Retry) {
                hasRetryAnnotation = true;
                value = ((Retry) annotation).value();
            }
        }
        final boolean shouldRetryCall = hasRetryAnnotation;
        final int maxRetries = value;
        final CallAdapter<?, ?> delegate = retrofit.nextCallAdapter(this, returnType, annotations);
        return new CallAdapter<Object, Object>() {
            @SuppressWarnings("unchecked")
            @Override
            public Object adapt(@NonNull Call call) {
                return delegate.adapt(
                        shouldRetryCall ? new RetryingCall<>(call, mExecutor, maxRetries) : call);
            }

            @Override
            public Type responseType() {
                return delegate.responseType();
            }
        };
    }
}