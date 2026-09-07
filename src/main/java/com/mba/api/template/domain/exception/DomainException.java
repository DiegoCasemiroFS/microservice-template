package com.mba.api.template.domain.exception;

/**
 * Base para as exceções de negócio da aplicação.
 *
 * <p>Não conhece HTTP nem framework: a tradução para status e corpo de resposta
 * é responsabilidade do {@code GlobalExceptionHandler}, em {@code infra.exception}.
 *
 * <p>O {@code code} é um identificador estável destinado ao consumo programático
 * pelo cliente. A mensagem é para leitura humana e pode mudar sem quebrar contrato.
 *
 * <p>Estenda esta classe para criar exceções próprias do seu domínio. Se não houver
 * handler dedicado, o handler de {@code DomainException} responde com 422.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected DomainException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}