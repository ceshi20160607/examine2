package com.unique.examine.openapi.security;

import com.unique.examine.core.api.PlatformOpenApiPrincipalFacade;
import com.unique.examine.openapi.domain.OpenApiCredential;
import com.unique.examine.openapi.domain.PlatformOpenApiApplication;
import com.unique.examine.openapi.repository.PlatformOpenApiRepository;
import com.unique.examine.openapi.secret.SecretRefResolver;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

public class PlatformOpenApiAuthenticator {
    private final PlatformOpenApiRepository repository; private final SecretRefResolver secrets;
    private final PlatformOpenApiPrincipalFacade principals; private final Clock clock;
    public PlatformOpenApiAuthenticator(PlatformOpenApiRepository repository,SecretRefResolver secrets,PlatformOpenApiPrincipalFacade principals,Clock clock){this.repository=repository;this.secrets=secrets;this.principals=principals;this.clock=clock;}
    @Transactional public PlatformOpenApiAuthentication authenticate(Request request,OpenApiAttempt attempt){
        var now=clock.instant(); Instant signed;try{signed=Instant.ofEpochSecond(Long.parseLong(request.headers().timestamp()));}catch(RuntimeException e){throw OpenApiSecurityErrors.timestampInvalid();}
        if(Duration.between(signed,now).abs().compareTo(OpenApiAuthenticator.SIGNATURE_WINDOW)>0)throw OpenApiSecurityErrors.timestampInvalid();
        var bundle=repository.findByAppKey(request.headers().appKey()).orElseThrow(OpenApiSecurityErrors::appUnavailable);var app=bundle.application();var credential=bundle.credential();attempt.identified(app.id(),credential.credentialVersion());
        if(app.status()!= PlatformOpenApiApplication.Status.ACTIVE||credential.status()!= OpenApiCredential.Status.ACTIVE||credential.credentialVersion()!=app.currentCredentialVersion())throw OpenApiSecurityErrors.appUnavailable();
        var secret=secrets.resolve(credential.secretRef()).orElseThrow(OpenApiSecurityErrors::credentialUnavailable);try{var canonical=OpenApiCanonicalRequest.canonical(request.method(),request.path(),request.query(),request.body(),request.headers().timestamp(),request.headers().nonce(),request.headers().idempotencyKey());if(!OpenApiCanonicalRequest.verify(secret,canonical,request.headers().signature()))throw OpenApiSecurityErrors.signatureInvalid();}catch(IllegalArgumentException e){throw OpenApiSecurityErrors.signatureInvalid();}finally{Arrays.fill(secret,(byte)0);}
        if(!IpAllowlist.allows(app.ipAllowlist(),request.ip()))throw OpenApiSecurityErrors.ipDenied();if(!app.scopes().contains(request.route().requiredScope()))throw OpenApiSecurityErrors.scopeDenied();
        var principal=principals.resolve(app.serviceAccountId());if(principal==null||!principal.accountActive()||principal.accountId()<=0||!principal.permissions().contains(request.route().requiredPermission()))throw OpenApiSecurityErrors.permissionDenied();
        var window=now.truncatedTo(ChronoUnit.MINUTES);var bucket=repository.lockRateBucket(app.id(),window);if(bucket.requestCount()>=app.rateLimitPerMinute()||!repository.incrementRateBucket(app.id(),window,bucket.requestCount(),bucket.version()))throw OpenApiSecurityErrors.rateLimited(Math.max(1,60-now.getEpochSecond()%60),app.rateLimitPerMinute());
        if(!repository.consumeNonce(app.id(),credential.credentialVersion(),request.headers().nonce(),now.plus(OpenApiAuthenticator.SIGNATURE_WINDOW),now))throw OpenApiSecurityErrors.replayDetected();
        return new PlatformOpenApiAuthentication(app,credential,new PlatformOpenApiMachineSession(app.id(),principal.accountId(),principal.permissionVersion(),principal.permissions()));
    }
    public record Request(String method,String path,String query,byte[] body,String ip,OpenApiHeaders headers,PlatformOpenApiRoutePolicy route){public Request{body=body==null?new byte[0]:body.clone();}public byte[] body(){return body.clone();}}
}
