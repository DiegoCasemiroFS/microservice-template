package com.mba.api.template.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mba.api.template.infra.i18n.Messages;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;

/**
 * Constrói as respostas 401 e 403 da camada de segurança no mesmo formato ProblemDetail
 * (RFC 9457) usado pelo {@code GlobalExceptionHandler}.
 *
 * <p>Sem esta classe, o Spring Security responderia com o corpo padrão dele — diferente
 * do formato usado pelo resto da aplicação — quebrando a padronização de contratos de
 * erro que é um dos objetivos deste template.
 *
 * <p>Também é um {@code @RestControllerAdvice} porque a negação de {@code @PreAuthorize}
 * chega ao Spring MVC como uma exceção lançada durante a invocação do controller: o
 * Spring resolve {@code @ExceptionHandler} antes que essa exceção possa se propagar até
 * o {@code AccessDeniedHandler} configurado na cadeia de segurança. Sem o método
 * {@link #propagateAccessDenied(AccessDeniedException)} abaixo, o
 * {@code GlobalExceptionHandler} capturaria a negação primeiro, pelo seu handler
 * genérico de {@code Exception}, e devolveria 500 em vez de 403.
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} é necessário porque o Spring escolhe entre
 * {@code @ControllerAdvice} beans DIFERENTES pela ordem do bean, não pela
 * especificidade do tipo de exceção — ao contrário do que ocorre entre métodos de um
 * mesmo bean. Sem esta anotação, o handler genérico de {@code Exception} do
 * {@code GlobalExceptionHandler} poderia ser escolhido primeiro mesmo havendo, aqui,
 * um handler mais específico para {@link AccessDeniedException}.
 */
@Component
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityErrorHandlers {

    private static final String UNAUTHORIZED_PROBLEM_TYPE = "urn:problem:unauthorized";
    private static final String FORBIDDEN_PROBLEM_TYPE = "urn:problem:forbidden";

    private final Messages messages;
    private final ObjectMapper objectMapper;

    /**
     * @param messages     fachada de i18n usada para resolver título e detalhe do erro
     * @param objectMapper serializador JSON compartilhado com o resto da aplicação;
     *                     recebe aqui o mixin de {@link ProblemDetail} e o módulo de
     *                     data/hora do Java 8, para garantir que as propriedades extras
     *                     (como {@code timestamp}, um {@link Instant}) sejam
     *                     serializadas corretamente no nível raiz do JSON,
     *                     independentemente de o mapper injetado já trazer esse suporte
     *                     registrado ou não
     */
    public SecurityErrorHandlers(Messages messages, ObjectMapper objectMapper) {
        this.messages = messages;
        this.objectMapper = objectMapper;
        this.objectMapper.addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Cria o {@link AuthenticationEntryPoint} usado quando a requisição não traz um
     * JWT válido: token ausente, malformado, com assinatura inválida, expirado ou com
     * emissor/audiência incorretos.
     *
     * @return handler que escreve a resposta 401 em formato ProblemDetail
     */
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, exception) ->
                writeProblem(response, HttpStatus.UNAUTHORIZED, UNAUTHORIZED_PROBLEM_TYPE,
                        "error.unauthorized.title", "error.unauthorized.detail");
    }

    /**
     * Cria o {@link AccessDeniedHandler} usado quando o JWT é válido, mas falta a
     * authority exigida pelo endpoint.
     *
     * @return handler que escreve a resposta 403 em formato ProblemDetail
     */
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) ->
                writeProblem(response, HttpStatus.FORBIDDEN, FORBIDDEN_PROBLEM_TYPE,
                        "error.forbidden.title", "error.forbidden.detail");
    }

    /**
     * Relança a negação de autorização para que ela saia do Spring MVC e chegue ao
     * {@link #accessDeniedHandler()} configurado na cadeia de segurança, em vez de ser
     * absorvida pelo handler genérico de {@code Exception} do {@code GlobalExceptionHandler}.
     *
     * @param exception a negação de autorização lançada por {@code @PreAuthorize}
     * @throws AccessDeniedException sempre — a própria exceção recebida, propagada
     */
    @ExceptionHandler(AccessDeniedException.class)
    void propagateAccessDenied(AccessDeniedException exception) throws AccessDeniedException {
        throw exception;
    }

    private void writeProblem(HttpServletResponse response, HttpStatus status, String type,
                               String titleKey, String detailKey) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, messages.get(detailKey));
        problem.setTitle(messages.get(titleKey));
        problem.setType(URI.create(type));
        problem.setProperty("timestamp", Instant.now());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
