package com.mba.api.template.infra.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolve textos externalizados de {@code messages.properties}.
 *
 * <p>Disponível para injeção em qualquer componente: handlers de exceção,
 * controllers, serviços, geradores de notificação. Poupa o chamador de lidar
 * com {@code Locale} e com o tratamento de chave ausente.
 *
 * <p>Para cadastrar ou alterar textos, edite
 * {@code src/main/resources/messages.properties}.
 */
@Component
public class Messages {

    private final MessageSource messageSource;

    public Messages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Retorna o texto da chave, no idioma da requisição corrente.
     *
     * <p>Quando a chave não existe, devolve a própria chave em vez de lançar
     * exceção — evita que um texto não cadastrado interrompa a requisição.
     *
     * @param key  chave definida em messages.properties
     * @param args valores para os placeholders {0}, {1}...
     */
    public String get(String key, Object... args) {
        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }
}