package com.illarilegal.pago;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import java.time.Duration;

/** The public issuer and the internal JWKS URL serve different purposes. Both are validated. */
@Configuration
public class JwtValidationConfig {
    @Bean @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwks,
            @Value("${illari.security.audience:illari-api}") String audience) {
        var decoder=NimbusJwtDecoder.withJwkSetUri(jwks)
                .jwsAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).build();
        OAuth2TokenValidator<Jwt> claims=jwt -> jwt.getExpiresAt()!=null
                && jwt.getSubject()!=null && !jwt.getSubject().isBlank()
                && jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Required subject, expiration or audience missing.",null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(issuer),new JwtTimestampValidator(Duration.ofSeconds(5)),claims));
        return decoder;
    }
}

