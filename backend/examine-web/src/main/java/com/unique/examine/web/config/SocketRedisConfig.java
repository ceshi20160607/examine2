package com.unique.examine.web.config;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@Configuration
public class SocketRedisConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "unexamine.redis.socket-template", name = "enabled", havingValue = "true")
    public StringRedisTemplate socketStringRedisTemplate(SocketRedisClient client) {
        return new SocketStringRedisTemplate(client);
    }

    @Bean
    @ConditionalOnProperty(prefix = "unexamine.redis.socket-template", name = "enabled", havingValue = "true")
    public SocketRedisClient socketRedisClient(
            @Value("${spring.data.redis.host:127.0.0.1}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.database:0}") int database,
            @Value("${spring.data.redis.timeout:3s}") Duration timeout) {
        return new SocketRedisClient(host, port, password, database, timeout);
    }

    static final class SocketStringRedisTemplate extends StringRedisTemplate {

        private final SocketRedisClient client;
        private final ValueOperations<String, String> valueOperations;

        SocketStringRedisTemplate(SocketRedisClient client) {
            this.client = client;
            this.valueOperations = createValueOperations(client);
        }

        @Override
        public void afterPropertiesSet() {
            // This opt-in template does not use Spring Data's connection factory.
        }

        @Override
        public ValueOperations<String, String> opsForValue() {
            return valueOperations;
        }

        @Override
        public Boolean delete(String key) {
            return client.delete(key) > 0;
        }

        @Override
        public Long delete(Collection<String> keys) {
            if (keys == null || keys.isEmpty()) {
                return 0L;
            }
            return client.delete(keys.toArray(String[]::new));
        }

        @Override
        public Boolean expire(String key, long timeout, TimeUnit unit) {
            return client.expire(key, Duration.ofMillis(unit.toMillis(timeout)));
        }

        @Override
        public <T> T execute(RedisCallback<T> action) {
            return action.doInRedis(redisConnectionProxy(client));
        }
    }

    static final class SocketRedisClient {

        private final String host;
        private final int port;
        private final String password;
        private final int database;
        private final Duration timeout;

        SocketRedisClient(String host, int port, String password, int database, Duration timeout) {
            this.host = host;
            this.port = port;
            this.password = password == null ? "" : password;
            this.database = database;
            this.timeout = timeout == null ? Duration.ofSeconds(3) : timeout;
        }

        String ping() {
            Object result = execute(List.of("PING"));
            return result == null ? null : String.valueOf(result);
        }

        String get(String key) {
            Object result = execute(List.of("GET", key));
            return result == null ? null : String.valueOf(result);
        }

        void set(String key, String value) {
            execute(List.of("SET", key, value));
        }

        void set(String key, String value, Duration ttl) {
            long milliseconds = Math.max(1L, ttl.toMillis());
            execute(List.of("SET", key, value, "PX", String.valueOf(milliseconds)));
        }

        long delete(String... keys) {
            List<String> command = new ArrayList<>();
            command.add("DEL");
            command.addAll(List.of(keys));
            Object result = execute(command);
            return result instanceof Number number ? number.longValue() : 0L;
        }

        boolean expire(String key, Duration ttl) {
            long milliseconds = Math.max(1L, ttl.toMillis());
            Object result = execute(List.of("PEXPIRE", key, String.valueOf(milliseconds)));
            return result instanceof Number number && number.longValue() == 1L;
        }

        private Object execute(List<String> command) {
            try (RedisSocket socket = connect()) {
                return socket.command(command);
            } catch (IOException ex) {
                throw new RedisSystemException("Redis socket command failed: " + command.get(0), ex);
            }
        }

        private RedisSocket connect() throws IOException {
            RedisSocket redis = new RedisSocket(host, port, timeout);
            try {
                if (!password.isBlank()) {
                    redis.command(List.of("AUTH", password));
                }
                if (database > 0) {
                    redis.command(List.of("SELECT", String.valueOf(database)));
                }
                return redis;
            } catch (IOException | RuntimeException ex) {
                redis.close();
                throw ex;
            }
        }
    }

    private static ValueOperations<String, String> createValueOperations(SocketRedisClient client) {
        return (ValueOperations<String, String>) Proxy.newProxyInstance(
                ValueOperations.class.getClassLoader(),
                new Class<?>[]{ValueOperations.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("get".equals(name)) {
                        return client.get(String.valueOf(args[0]));
                    }
                    if ("set".equals(name)) {
                        if (args.length == 2) {
                            client.set(String.valueOf(args[0]), String.valueOf(args[1]));
                            return null;
                        }
                        if (args.length == 3 && args[2] instanceof Duration duration) {
                            client.set(String.valueOf(args[0]), String.valueOf(args[1]), duration);
                            return null;
                        }
                        if (args.length == 4 && args[2] instanceof Number timeout
                                && args[3] instanceof TimeUnit unit) {
                            client.set(String.valueOf(args[0]), String.valueOf(args[1]),
                                    Duration.ofMillis(unit.toMillis(timeout.longValue())));
                            return null;
                        }
                    }
                    if ("toString".equals(name)) {
                        return "SocketValueOperations";
                    }
                    throw new UnsupportedOperationException("Socket Redis ValueOperations does not support " + name);
                });
    }

    private static RedisConnection redisConnectionProxy(SocketRedisClient client) {
        return (RedisConnection) Proxy.newProxyInstance(
                RedisConnection.class.getClassLoader(),
                new Class<?>[]{RedisConnection.class},
                (proxy, method, args) -> {
                    if ("ping".equals(method.getName())) {
                        return client.ping();
                    }
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    if ("isClosed".equals(method.getName())) {
                        return false;
                    }
                    if ("toString".equals(method.getName())) {
                        return "SocketRedisConnection";
                    }
                    throw new UnsupportedOperationException("Socket Redis connection does not support "
                            + method.getName());
                });
    }

    static final class RedisSocket implements Closeable {

        private final Socket socket;
        private final BufferedInputStream input;
        private final BufferedOutputStream output;

        RedisSocket(String host, int port, Duration timeout) throws IOException {
            this.socket = new Socket();
            int timeoutMs = Math.toIntExact(Math.max(1000L, timeout.toMillis()));
            this.socket.connect(new InetSocketAddress(host, port), timeoutMs);
            this.socket.setSoTimeout(timeoutMs);
            this.input = new BufferedInputStream(socket.getInputStream());
            this.output = new BufferedOutputStream(socket.getOutputStream());
        }

        Object command(List<String> parts) throws IOException {
            output.write(("*" + parts.size() + "\r\n").getBytes(StandardCharsets.UTF_8));
            for (String part : parts) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                output.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
                output.write(bytes);
                output.write("\r\n".getBytes(StandardCharsets.US_ASCII));
            }
            output.flush();
            return readReply();
        }

        private Object readReply() throws IOException {
            int type = input.read();
            if (type < 0) {
                throw new IOException("Redis closed the connection");
            }
            return switch ((char) type) {
                case '+' -> readLine();
                case '-' -> throw new IOException("Redis error: " + readLine());
                case ':' -> Long.parseLong(readLine());
                case '$' -> readBulkString();
                case '*' -> readArray();
                default -> throw new IOException("Unexpected Redis reply type: " + (char) type);
            };
        }

        private String readBulkString() throws IOException {
            int length = Integer.parseInt(readLine());
            if (length < 0) {
                return null;
            }
            byte[] bytes = input.readNBytes(length);
            if (bytes.length != length) {
                throw new IOException("Incomplete Redis bulk reply");
            }
            expectCrLf();
            return new String(bytes, StandardCharsets.UTF_8);
        }

        private List<Object> readArray() throws IOException {
            int length = Integer.parseInt(readLine());
            List<Object> result = new ArrayList<>(Math.max(0, length));
            for (int i = 0; i < length; i++) {
                result.add(readReply());
            }
            return result;
        }

        private String readLine() throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int previous = -1;
            int current;
            while ((current = input.read()) >= 0) {
                if (previous == '\r' && current == '\n') {
                    byte[] bytes = line.toByteArray();
                    return new String(bytes, 0, bytes.length - 1, StandardCharsets.UTF_8);
                }
                line.write(current);
                previous = current;
            }
            throw new IOException("Unexpected end of Redis line");
        }

        private void expectCrLf() throws IOException {
            int cr = input.read();
            int lf = input.read();
            if (cr != '\r' || lf != '\n') {
                throw new IOException("Invalid Redis line terminator");
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
