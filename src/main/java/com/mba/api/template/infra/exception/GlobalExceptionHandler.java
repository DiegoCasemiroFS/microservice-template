package com.mba.api.template.infra.exception;

import com.mba.api.template.domain.exception.BusinessException;
import com.mba.api.template.domain.exception.ConflictException;
import com.mba.api.template.domain.exception.DomainException;
import com.mba.api.template.domain.exception.ResourceNotFoundException;
import com.mba.api.template.infra.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Único ponto de tradução entre exceções e respostas HTTP.
 *
 * <p>As exceções vivem em {@code domain.exception} e não conhecem HTTP. É aqui
 * que cada uma ganha status, título e corpo, no formato ProblemDetail (RFC 9457).
 * Nenhum controller deve montar resposta de erro por conta própria.
 *
 * <p>Os handlers são ordenados do mais específico para o mais genérico. O Spring
 * escolhe sempre o mais específico que casa com a exceção lançada.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Messages messages;

    public GlobalExceptionHandler(Messages messages) {
        this.messages = messages;
    }

    /**
     * Regra de negócio recusou uma requisição sintaticamente válida.
     */
    @ExceptionHandler(BusinessException.class)
    ProblemDetail handleBusiness(BusinessException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, messages.get("error.business.title"),
                ex.getMessage(), ex.getCode());
    }

    /**
     * Recurso solicitado não existe.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, messages.get("error.notfound.title"),
                ex.getMessage(), ex.getCode());
    }

    /**
     * Estado atual do recurso impede a operação: duplicidade, concorrência, transição inválida.
     */
    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, messages.get("error.conflict.title"),
                ex.getMessage(), ex.getCode());
    }

    /**
     * Rede de segurança para exceções de domínio criadas pelo desenvolvedor
     * sem handler próprio.
     *
     * <p>Sem este método, uma subclasse nova de {@code DomainException} cairia no
     * handler de {@code Exception} e retornaria 500 — reportando erro do cliente
     * como falha do servidor. Aqui ela vira 422, que é o comportamento correto
     * para violação de regra de negócio.
     *
     * <p>O aviso em log sinaliza que essa exceção talvez mereça um handler
     * dedicado, caso o status apropriado não seja 422.
     */
    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomain(DomainException ex) {
        log.warn("Exceção de domínio sem handler dedicado: {}", ex.getClass().getName());
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, messages.get("error.domain.title"),
                ex.getMessage(), ex.getCode());
    }

    /**
     * Falha de Bean Validation em corpo anotado com {@code @Valid}.
     *
     * <p>Devolve o mapa campo/mensagem na propriedade {@code errors} para que o
     * cliente possa destacar os campos inválidos no formulário.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        var problem = problem(HttpStatus.BAD_REQUEST, messages.get("error.validation.title"),
                messages.get("error.validation.detail"), "VALIDATION_ERROR");
        problem.setProperty("errors", errors);
        return problem;
    }

    /**
     * Corpo ausente, JSON malformado ou tipo incompatível na desserialização.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleMalformedBody(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, messages.get("error.malformed.title"),
                messages.get("error.malformed.detail"), "MALFORMED_BODY");
    }

    /**
     * Parâmetro de rota ou query com tipo incompatível, por exemplo texto onde se espera número.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, messages.get("error.parameter.title"),
                messages.get("error.parameter.detail", ex.getName()),
                "PARAMETER_TYPE_MISMATCH");
    }

    /**
     * Última linha de defesa: qualquer exceção não prevista.
     *
     * <p>O stack trace vai para o log e o cliente recebe mensagem genérica.
     * Isso evita vazar detalhe de implementação — nome de classe, consulta SQL,
     * caminho de arquivo — na resposta HTTP.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Erro não tratado", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("error.internal.title"),
                messages.get("error.internal.detail"), "INTERNAL_ERROR");
    }

    /**
     * Monta o ProblemDetail com os campos que toda resposta de erro deve ter.
     *
     * @param code identificador estável do erro, destinado ao consumo programático
     *             pelo cliente; a mensagem é para leitura humana e pode mudar
     */
    private ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("urn:problem:" + code.toLowerCase().replace('_', '-')));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}