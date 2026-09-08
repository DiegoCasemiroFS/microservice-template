package com.mba.api.template.infra.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mba.api.template.infra.i18n.Messages;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários do formato de resposta gerado para 401 e 403.
 *
 * <p>Não sobe contexto Spring. O mock de {@link Messages} devolve valores DECLARADOS
 * neste teste (não um comportamento genérico), porque o que se verifica aqui é a
 * montagem do corpo JSON — a resolução real de i18n já é responsabilidade de
 * {@link Messages} e não precisa ser reexercitada aqui.
 */
class SecurityErrorHandlersTest {

    private static final String UNAUTHORIZED_TITLE = "Não autenticado";
    private static final String UNAUTHORIZED_DETAIL = "É necessário um token de acesso válido.";
    private static final String FORBIDDEN_TITLE = "Acesso negado";
    private static final String FORBIDDEN_DETAIL = "Você não tem permissão para acessar este recurso.";

    private SecurityErrorHandlers handlers;

    @BeforeEach
    void setUp() {
        Messages messages = Mockito.mock(Messages.class);
        Mockito.when(messages.get("error.unauthorized.title")).thenReturn(UNAUTHORIZED_TITLE);
        Mockito.when(messages.get("error.unauthorized.detail")).thenReturn(UNAUTHORIZED_DETAIL);
        Mockito.when(messages.get("error.forbidden.title")).thenReturn(FORBIDDEN_TITLE);
        Mockito.when(messages.get("error.forbidden.detail")).thenReturn(FORBIDDEN_DETAIL);

        handlers = new SecurityErrorHandlers(messages, new ObjectMapper());
    }

    @Test
    @DisplayName("escreve 401 com título, detalhe, status e timestamp")
    void writesUnauthorizedBody() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handlers.authenticationEntryPoint().commence(null, response, null);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.get("title").asText()).isEqualTo(UNAUTHORIZED_TITLE);
        assertThat(body.get("detail").asText()).isEqualTo(UNAUTHORIZED_DETAIL);
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.has("timestamp")).isTrue();
    }

    @Test
    @DisplayName("escreve 403 com título, detalhe, status e timestamp")
    void writesForbiddenBody() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handlers.accessDeniedHandler().handle(null, response, null);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.get("title").asText()).isEqualTo(FORBIDDEN_TITLE);
        assertThat(body.get("detail").asText()).isEqualTo(FORBIDDEN_DETAIL);
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.has("timestamp")).isTrue();
    }
}
