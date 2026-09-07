package com.mba.api.template.domain.exception;

/**
 * Regra de negócio recusou uma requisição sintaticamente válida.
 *
 * <p>Traduzida em HTTP 422. Use quando a requisição está bem formada e os campos
 * são válidos, mas o domínio não permite a operação.
 *
 * <p>Exemplo: {@code throw new BusinessException("INSUFFICIENT_BALANCE", "Saldo insuficiente.")}
 */
public class BusinessException extends DomainException {

    public BusinessException(String code, String message) {
        super(code, message);
    }

    public BusinessException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}