package com.mba.api.template.infra.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Gera, em memória, um par de chaves RSA e assina tokens JWT reais para os testes de
 * integração da camada de segurança.
 *
 * <p>Não é um teste em si: é o apoio que permite aos testes exercitarem o mesmo caminho
 * de validação de assinatura, emissor, expiração e audiência que roda em produção, sem
 * depender de um Identity Provider real e sem recorrer a mocks de autenticação.
 */
public final class JwtTestSupport {

    /** Emissor usado em todos os tokens assinados por esta instância. */
    public static final String ISSUER = "https://issuer.test";

    /** Audiência esperada pela aplicação nos testes de integração. */
    public static final String AUDIENCE = "template-service";

    private static final int KEY_SIZE_BITS = 2048;
    private static final long TOKEN_LIFETIME_SECONDS = 300;
    private static final long EXPIRED_TOKEN_AGE_SECONDS = 7200;
    private static final long EXPIRED_TOKEN_ORIGINAL_LIFETIME_SECONDS = 60;

    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;

    /**
     * Gera um novo par de chaves RSA de 2048 bits em memória, exclusivo desta instância.
     */
    public JwtTestSupport() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(KEY_SIZE_BITS);
            KeyPair pair = generator.generateKeyPair();
            this.publicKey = (RSAPublicKey) pair.getPublic();
            this.privateKey = (RSAPrivateKey) pair.getPrivate();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Algoritmo RSA indisponível na JVM de testes", ex);
        }
    }

    /**
     * Codifica a chave pública desta instância em PEM (X.509 SubjectPublicKeyInfo),
     * formato aceito por {@code spring.security.oauth2.resourceserver.jwt.public-key-location}.
     *
     * @return a chave pública em texto PEM
     */
    public String publicKeyAsPem() {
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----\n";
    }

    /**
     * Assina um token válido, com o emissor e a audiência que a aplicação espera.
     *
     * @param subject claim {@code sub} do token
     * @param scope   valor do claim {@code scope}, mapeado em authority pela aplicação
     * @return o JWT compacto e assinado
     */
    public String validToken(String subject, String scope) {
        Instant now = Instant.now();
        return sign(claims(subject, now, now.plusSeconds(TOKEN_LIFETIME_SECONDS), AUDIENCE, scope));
    }

    /**
     * Assina um token válido, porém com audiência de outro serviço — prova que a
     * validação de audiência recusa tokens emitidos para um consumidor diferente.
     *
     * @param subject claim {@code sub} do token
     * @return o JWT compacto e assinado
     */
    public String tokenForAnotherAudience(String subject) {
        Instant now = Instant.now();
        return sign(claims(subject, now, now.plusSeconds(TOKEN_LIFETIME_SECONDS), "another-service", null));
    }

    /**
     * Assina um token cuja expiração já ocorreu.
     *
     * @param subject claim {@code sub} do token
     * @return o JWT compacto e assinado
     */
    public String expiredToken(String subject) {
        Instant issuedInThePast = Instant.now().minusSeconds(EXPIRED_TOKEN_AGE_SECONDS);
        Instant expiredSince = issuedInThePast.plusSeconds(EXPIRED_TOKEN_ORIGINAL_LIFETIME_SECONDS);
        return sign(claims(subject, issuedInThePast, expiredSince, AUDIENCE, null));
    }

    private JWTClaimsSet claims(String subject, Instant issuedAt, Instant expiresAt, String audience, String scope) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(ISSUER)
                .audience(audience)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt));
        if (scope != null) {
            builder.claim("scope", scope);
        }
        return builder.build();
    }

    private String sign(JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Não foi possível assinar o token de teste", ex);
        }
    }
}
