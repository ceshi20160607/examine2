package com.unique.examine.openapi.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;

final class BufferedOpenApiRequest extends HttpServletRequestWrapper {
    static final int MAXIMUM_BODY_BYTES = 1_048_576;
    private final byte[] body;

    BufferedOpenApiRequest(HttpServletRequest request) throws IOException {
        super(request);
        var value = request.getInputStream().readNBytes(MAXIMUM_BODY_BYTES + 1);
        if (value.length > MAXIMUM_BODY_BYTES) {
            throw OpenApiSecurityErrors.authenticationRequired();
        }
        this.body = value;
    }

    byte[] bodyBytes() {
        return body.clone();
    }

    @Override
    public ServletInputStream getInputStream() {
        var input = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return input.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                if (readListener == null) {
                    throw new IllegalArgumentException("readListener is required");
                }
            }

            @Override
            public int read() {
                return input.read();
            }
        };
    }
}
