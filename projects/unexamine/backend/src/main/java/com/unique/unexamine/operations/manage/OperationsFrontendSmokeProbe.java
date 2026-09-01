package com.unique.unexamine.operations.manage;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.HttpURLConnection;
import java.time.Duration;

@Component
public class OperationsFrontendSmokeProbe {
    public Result inspect(String url) {
        long started = System.nanoTime();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(3_000);
            connection.setReadTimeout(5_000);
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(true);
            int status = connection.getResponseCode();
            return new Result(status >= 200 && status < 400, status,
                    Duration.ofNanos(System.nanoTime() - started).toMillis(), null);
        } catch (Exception exception) {
            return new Result(false, null, Duration.ofNanos(System.nanoTime() - started).toMillis(),
                    exception.getClass().getSimpleName());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public record Result(boolean passed, Integer statusCode, long durationMillis, String errorType) { }
}
