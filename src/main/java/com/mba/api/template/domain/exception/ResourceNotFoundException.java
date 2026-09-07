package com.mba.api.template.domain.exception;

/**
 * Recurso solicitado não existe.
 *
 * <p>Traduzida em HTTP 404.
 *
 * <p>Prefira a fábrica {@link #of(String, Object)} para manter a mensagem padronizada:
 * {@code throw ResourceNotFoundException.of("Usuário", id)}
 */
public class ResourceNotFoundException extends DomainException {

    private static final String DEFAULT_CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String code, String message) {
        super(code, message);
    }

    /**
     * Cria a exceção com mensagem padronizada a partir do nome do recurso e do identificador.
     *
     * @param resource nome do recurso, como exposto ao usuário
     * @param id       identificador que não foi encontrado
     */
    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException(
                DEFAULT_CODE,
                "%s não encontrado: %s".formatted(resource, id));
    }
}