package com.mba.api.template.infra.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração da cadeia de segurança, de ponta a ponta.
 *
 * <p>Sem dados simulados: os tokens usados aqui são realmente assinados por
 * {@link JwtTestSupport} e realmente validados pelo mesmo caminho de código que roda em
 * produção — assinatura, emissor, expiração e audiência. A chave pública é gravada em
 * arquivo temporário e apontada via {@code public-key-location}, o que dispensa um
 * Identity Provider real e evita qualquer chamada de rede durante os testes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecuredController.class)
class SecurityIntegrationTest {

    private static final JwtTestSupport TOKENS = new JwtTestSupport();
    private static final String SUBJECT = "diego";

    @TempDir
    static Path keyDirectory;

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void securitySettings(DynamicPropertyRegistry registry) throws IOException {
        Path publicKeyFile = keyDirectory.resolve("public-key.pem");
        Files.writeString(publicKeyFile, TOKENS.publicKeyAsPem());

        registry.add("app.security.enabled", () -> "true");
        registry.add("app.security.public-paths", () -> "/public/ping");
        registry.add("spring.security.oauth2.resourceserver.jwt.public-key-location",
                () -> "file:" + publicKeyFile.toAbsolutePath());
        registry.add("spring.security.oauth2.resourceserver.jwt.audiences", () -> JwtTestSupport.AUDIENCE);
    }

    @Test
    @DisplayName("caminho público é acessível sem token")
    void allowsPublicPathWithoutToken() throws Exception {
        mockMvc.perform(get("/public/ping")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("caminho protegido sem token é recusado")
    void refusesProtectedPathWithoutToken() throws Exception {
        mockMvc.perform(get("/secured/ping")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("token malformado é recusado")
    void refusesMalformedToken() throws Exception {
        mockMvc.perform(get("/secured/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer isto-nao-e-um-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("token expirado é recusado")
    void refusesExpiredToken() throws Exception {
        String token = TOKENS.expiredToken(SUBJECT);

        mockMvc.perform(get("/secured/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("token com audiência de outro serviço é recusado")
    void refusesForeignAudience() throws Exception {
        String token = TOKENS.tokenForAnotherAudience(SUBJECT);

        mockMvc.perform(get("/secured/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("token válido com audiência correta é aceito")
    void acceptsValidToken() throws Exception {
        String token = TOKENS.validToken(SUBJECT, "read");

        mockMvc.perform(get("/secured/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("claim scope mapeado em authority suficiente libera o acesso")
    void mapsScopeToAuthorityAndAllows() throws Exception {
        String token = TOKENS.validToken(SUBJECT, "admin");

        mockMvc.perform(get("/secured/admin").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("authority insuficiente é recusada com 403")
    void refusesInsufficientAuthority() throws Exception {
        String token = TOKENS.validToken(SUBJECT, "read");

        mockMvc.perform(get("/secured/admin").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sonda de saúde é acessível sem token")
    void keepsHealthProbeOpen() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("nenhuma sessão HTTP é criada mesmo após requisição autenticada")
    void staysStateless() throws Exception {
        String token = TOKENS.validToken(SUBJECT, "read");

        mockMvc.perform(get("/secured/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    if (result.getRequest().getSession(false) != null) {
                        throw new AssertionError("Sessão HTTP não deveria ter sido criada");
                    }
                });
    }
}
