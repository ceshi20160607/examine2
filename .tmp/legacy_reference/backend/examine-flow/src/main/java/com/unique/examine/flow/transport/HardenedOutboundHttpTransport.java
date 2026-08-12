package com.unique.examine.flow.transport;

import com.unique.examine.core.api.OutboundHttpTransport;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal HTTP/1.1 HTTPS transport that connects to an already validated
 * public address, disables redirects by construction and keeps hostname
 * verification/SNI bound to the original host.
 */
public final class HardenedOutboundHttpTransport
        implements OutboundHttpTransport {
    static final int MAXIMUM_RESPONSE_BYTES = 65_536;
    private static final int MAXIMUM_HEADER_BYTES = 65_536;

    private final WebhookTargetPolicy targets;
    private final SSLSocketFactory sockets;

    public HardenedOutboundHttpTransport(WebhookTargetPolicy targets) {
        this(targets, (SSLSocketFactory) SSLSocketFactory.getDefault());
    }

    HardenedOutboundHttpTransport(
            WebhookTargetPolicy targets,
            SSLSocketFactory sockets
    ) {
        this.targets = Objects.requireNonNull(targets, "targets");
        this.sockets = Objects.requireNonNull(sockets, "sockets");
    }

    @Override
    public Response post(Request request) throws TransportException {
        var started = System.nanoTime();
        try {
            var uri = targets.requireSafe(request.uri().toString());
            var addresses = targets.resolveSafe(uri);
            var port = uri.getPort() == -1 ? 443 : uri.getPort();
            var timeoutMillis = Math.toIntExact(
                    request.timeout().toMillis());
            try (var plain = new Socket()) {
                plain.connect(
                        new InetSocketAddress(addresses.getFirst(), port),
                        timeoutMillis
                );
                plain.setSoTimeout(timeoutMillis);
                targets.requirePublic(plain.getInetAddress());
                try (var tls = (SSLSocket) sockets.createSocket(
                        plain,
                        WebhookTargetPolicy.canonicalHost(uri),
                        port,
                        true
                )) {
                    configureTls(tls, uri.getHost(), timeoutMillis);
                    tls.startHandshake();
                    writeRequest(tls, request, uri, port);
                    var response = readResponse(tls);
                    return new Response(
                            response.statusCode(),
                            response.body(),
                            Duration.ofNanos(
                                    System.nanoTime() - started)
                    );
                }
            }
        } catch (WebhookTargetPolicy.UnsafeWebhookTargetException failure) {
            throw new TransportException(
                    TransportException.Kind.UNSAFE_TARGET,
                    "Outbound target is not permitted",
                    failure
            );
        } catch (SocketTimeoutException failure) {
            throw new TransportException(
                    TransportException.Kind.TIMEOUT,
                    "Outbound request timed out",
                    failure
            );
        } catch (SSLException failure) {
            throw new TransportException(
                    TransportException.Kind.TLS,
                    "Outbound TLS negotiation failed",
                    failure
            );
        } catch (ResponseTooLargeException failure) {
            throw new TransportException(
                    TransportException.Kind.RESPONSE_TOO_LARGE,
                    "Outbound response exceeded the safe limit",
                    failure
            );
        } catch (IOException failure) {
            throw new TransportException(
                    TransportException.Kind.IO,
                    "Outbound request failed",
                    failure
            );
        }
    }

    private static void configureTls(
            SSLSocket socket,
            String hostname,
            int timeoutMillis
    ) throws IOException {
        socket.setSoTimeout(timeoutMillis);
        SSLParameters parameters = socket.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        parameters.setServerNames(ListSupport.sni(hostname));
        socket.setSSLParameters(parameters);
    }

    private static void writeRequest(
            SSLSocket socket,
            Request request,
            java.net.URI uri,
            int port
    ) throws IOException {
        var path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (uri.getRawQuery() != null) {
            path += "?" + uri.getRawQuery();
        }
        var host = WebhookTargetPolicy.canonicalHost(uri);
        var hostHeader = port == 443 ? host : host + ":" + port;
        var headers = new LinkedHashMap<String, String>();
        headers.put("Host", hostHeader);
        headers.put("Connection", "close");
        headers.put("Content-Length", Integer.toString(request.body().length));
        request.headers().forEach((name, value) -> {
            requireHeader(name, value);
            var normalized = name.toLowerCase(Locale.ROOT);
            if (!normalized.equals("host")
                    && !normalized.equals("connection")
                    && !normalized.equals("content-length")) {
                headers.put(name, value);
            }
        });
        var output = socket.getOutputStream();
        output.write(("POST " + path + " HTTP/1.1\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        for (var entry : headers.entrySet()) {
            output.write((entry.getKey() + ": " + entry.getValue() + "\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
        }
        output.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        output.write(request.body());
        output.flush();
    }

    private static RawResponse readResponse(SSLSocket socket)
            throws IOException {
        var input = new BufferedInputStream(socket.getInputStream());
        var statusLine = readLine(input, 8192);
        var parts = statusLine.split(" ", 3);
        if (parts.length < 2 || !parts[0].startsWith("HTTP/")) {
            throw new IOException("Invalid HTTP response status");
        }
        final int status;
        try {
            status = Integer.parseInt(parts[1]);
        } catch (NumberFormatException failure) {
            throw new IOException("Invalid HTTP response status", failure);
        }
        var headers = new LinkedHashMap<String, String>();
        var headerBytes = statusLine.length();
        while (true) {
            var line = readLine(input, MAXIMUM_HEADER_BYTES);
            headerBytes += line.length();
            if (headerBytes > MAXIMUM_HEADER_BYTES) {
                throw new IOException("HTTP response headers are too large");
            }
            if (line.isEmpty()) {
                break;
            }
            var colon = line.indexOf(':');
            if (colon <= 0) {
                throw new IOException("Invalid HTTP response header");
            }
            headers.put(
                    line.substring(0, colon).strip().toLowerCase(Locale.ROOT),
                    line.substring(colon + 1).strip()
            );
        }
        byte[] body;
        if ("chunked".equalsIgnoreCase(headers.get("transfer-encoding"))) {
            body = readChunked(input);
        } else if (headers.containsKey("content-length")) {
            final int length;
            try {
                length = Integer.parseInt(headers.get("content-length"));
            } catch (NumberFormatException failure) {
                throw new IOException(
                        "Invalid HTTP response content length", failure);
            }
            if (length < 0 || length > MAXIMUM_RESPONSE_BYTES) {
                throw new ResponseTooLargeException();
            }
            body = input.readNBytes(length);
            if (body.length != length) {
                throw new EOFException("Unexpected end of HTTP response");
            }
        } else {
            body = readUntilEnd(input);
        }
        return new RawResponse(status, body);
    }

    private static byte[] readChunked(BufferedInputStream input)
            throws IOException {
        var output = new ByteArrayOutputStream();
        while (true) {
            var line = readLine(input, 128);
            var separator = line.indexOf(';');
            var sizeValue = separator == -1
                    ? line
                    : line.substring(0, separator);
            final int size;
            try {
                size = Integer.parseInt(sizeValue.strip(), 16);
            } catch (NumberFormatException failure) {
                throw new IOException("Invalid HTTP chunk size", failure);
            }
            if (size == 0) {
                while (!readLine(input, 8192).isEmpty()) {
                    // consume trailers
                }
                return output.toByteArray();
            }
            if (size < 0
                    || output.size() + (long) size
                    > MAXIMUM_RESPONSE_BYTES) {
                throw new ResponseTooLargeException();
            }
            var chunk = input.readNBytes(size);
            if (chunk.length != size) {
                throw new EOFException("Unexpected end of HTTP chunk");
            }
            output.write(chunk);
            if (!readLine(input, 2).isEmpty()) {
                throw new IOException("Invalid HTTP chunk delimiter");
            }
        }
    }

    private static byte[] readUntilEnd(BufferedInputStream input)
            throws IOException {
        var output = new ByteArrayOutputStream();
        var buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + (long) read > MAXIMUM_RESPONSE_BYTES) {
                throw new ResponseTooLargeException();
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String readLine(
            BufferedInputStream input,
            int maximum
    ) throws IOException {
        var output = new ByteArrayOutputStream();
        var previous = -1;
        while (output.size() <= maximum) {
            var current = input.read();
            if (current == -1) {
                throw new EOFException("Unexpected end of HTTP response");
            }
            if (previous == '\r' && current == '\n') {
                var bytes = output.toByteArray();
                return new String(
                        bytes, 0, Math.max(0, bytes.length - 1),
                        StandardCharsets.US_ASCII);
            }
            output.write(current);
            previous = current;
        }
        throw new IOException("HTTP line is too large");
    }

    private static void requireHeader(String name, String value) {
        if (name == null
                || !name.matches("^[A-Za-z0-9-]{1,64}$")
                || value == null
                || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0
                || value.length() > 2048) {
            throw new IllegalArgumentException(
                    "Outbound HTTP header is invalid");
        }
    }

    private record RawResponse(int statusCode, byte[] body) {
    }

    private static final class ResponseTooLargeException
            extends IOException {
    }

    private static final class ListSupport {
        private static java.util.List<SNIServerName> sni(String hostname) {
            return java.util.List.of(new SNIHostName(hostname));
        }
    }
}
