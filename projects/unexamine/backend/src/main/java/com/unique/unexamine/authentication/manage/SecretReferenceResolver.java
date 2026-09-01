package com.unique.unexamine.authentication.manage;

import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SecretReferenceResolver {
    private final Environment environment;

    public SecretReferenceResolver(Environment environment) {
        this.environment = environment;
    }

    public String resolve(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        String value;
        if (reference.startsWith("env:")) {
            value = System.getenv(reference.substring(4));
        } else if (reference.startsWith("property:")) {
            value = environment.getProperty(reference.substring(9));
        } else {
            throw new DomainException("SECRET_REFERENCE_UNSUPPORTED", "身份源密钥引用类型不受支持", HttpStatus.CONFLICT);
        }
        if (value == null || value.isBlank()) {
            throw new DomainException("SECRET_REFERENCE_UNAVAILABLE", "身份源密钥引用当前不可用", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return value;
    }
}
