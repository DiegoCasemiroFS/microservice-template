package com.mba.api.template.domain.exception;

/**
 * O estado atual do recurso impede a operação.
 *
 * <p>Traduzida em HTTP 409. Cobre três situações: duplicidade de recurso único,
 * conflito de concorrência (edição simultânea) e transição de estado inválida —
 * por exemplo, pagar um pedido já cancelado.
 *
 * <p>Exemplo: {@code throw new ConflictException("EMAIL_ALREADY_EXISTS", "E-mail já cadastrado.")}
 */
public class ConflictException extends DomainException {

    public ConflictException(String code, String message) {
        super(code, message);
    }

    public ConflictException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}