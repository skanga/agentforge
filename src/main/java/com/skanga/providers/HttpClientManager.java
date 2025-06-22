package com.skanga.providers;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class HttpClientManager {
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    private static final ThreadFactory THREAD_FACTORY = r -> {
        Thread t = new Thread(r, "http-client-" + THREAD_COUNTER.incrementAndGet());
        t.setDaemon(true);
        return t;
    };

    private static final HttpClient SHARED_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .executor(Executors.newCachedThreadPool(THREAD_FACTORY))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private HttpClientManager() {
        // Utility class
    }

    public static HttpClient getSharedClient() {
        return SHARED_CLIENT;
    }

    // For providers that need custom configurations
    public static HttpClient createCustomClient(Duration connectTimeout, Duration requestTimeout) {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .executor(Executors.newCachedThreadPool(THREAD_FACTORY))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
