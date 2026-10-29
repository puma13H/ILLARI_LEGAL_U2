package com.illarilegal.gateway;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import java.net.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class JwtValidationTest {
    static HttpServer server;
    static RSAKey key;
    static ReactiveJwtDecoder decoder;
    static final String ISSUER="https://issuer.test/realms/illari-legal";
    @BeforeAll static void setup() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var pair=generator.generateKeyPair();
        key=new RSAKey.Builder((RSAPublicKey)pair.getPublic()).privateKey((RSAPrivateKey)pair.getPrivate()).keyID("test-only").build();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        byte[] publicKeys=new JWKSet(key.toPublicJWK()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        server.createContext("/jwks",exchange->{exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,publicKeys.length);exchange.getResponseBody().write(publicKeys);exchange.close();});
        server.start();
        decoder=new JwtValidationConfig().jwtDecoder(ISSUER,"http://127.0.0.1:"+server.getAddress().getPort()+"/jwks","illari-api");
    }
    @AfterAll static void stop(){if(server!=null)server.stop(0);}
    static String token(String issuer,String audience,Instant expiry) throws Exception {
        var claims=new JWTClaimsSet.Builder().issuer(issuer).subject("test-user").audience(audience).issueTime(new Date());
        if(expiry!=null)claims.expirationTime(Date.from(expiry));
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(),claims.build());
        jwt.sign(new RSASSASigner(key));return jwt.serialize();
    }
    @Test void signatureIssuerAudienceAndExpirationAreAccepted() throws Exception {
        assertThat(decoder.decode(token(ISSUER,"illari-api",Instant.now().plusSeconds(120))).block().getSubject()).isEqualTo("test-user");
    }
    @Test void wrongAudienceIsRejected() throws Exception {
        String jwt=token(ISSUER,"other-api",Instant.now().plusSeconds(120));
        assertThatThrownBy(()->decoder.decode(jwt).block()).isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }
    @Test void wrongIssuerIsRejected() throws Exception {
        String jwt=token("https://different-issuer.test","illari-api",Instant.now().plusSeconds(120));
        assertThatThrownBy(()->decoder.decode(jwt).block()).isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }
    @Test void expiredSignedTokenIsRejected() throws Exception {
        String jwt=token(ISSUER,"illari-api",Instant.now().minusSeconds(20));
        assertThatThrownBy(()->decoder.decode(jwt).block()).isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }
    @Test void missingExpirationIsRejected() throws Exception {
        String jwt=token(ISSUER,"illari-api",null);
        assertThatThrownBy(()->decoder.decode(jwt).block()).isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }
    @Test void changedSignatureIsRejected() throws Exception {
        String jwt=token(ISSUER,"illari-api",Instant.now().plusSeconds(120));
        var parts=jwt.split("\\.");parts[2]=(parts[2].startsWith("A")?"B":"A")+parts[2].substring(1);
        String tampered=String.join(".",parts);
        assertThatThrownBy(()->decoder.decode(tampered).block()).isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }
}

