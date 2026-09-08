package com.mba.api.template.infra.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Configuração externalizada da camada de segurança, sob o prefixo {@code app.security}.
 *
 * <p>Segurança é apontada como o atributo de qualidade nº1 em importância e, ao mesmo
 * tempo, o 2º maior desafio de design entre 106 praticantes de microsserviços
 * (Waseem et al., 2021). Expor a política de segurança inteira como configuração
 * tipada — em vez de exigir alteração de código para ativar, desativar ou ajustar
 * o mapeamento de authorities — é a forma deste template reduzir o custo de adoção
 * dessa prática recomendada pela literatura, mas pouco padronizada na indústria.
 *
 * @param enabled          chave mestra da camada de segurança; {@code false} desliga
 *                         toda a proteção de endpoints e é destinado apenas a
 *                         desenvolvimento local, nunca a produção
 * @param authoritiesClaim nome do claim do JWT que carrega os papéis do usuário
 * @param authorityPrefix  prefixo aplicado a cada authority extraída do claim
 * @param publicPaths      caminhos liberados sem token, além de {@code /actuator/health/**},
 *                         que é sempre público independentemente desta lista
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("scope") String authoritiesClaim,
        @DefaultValue("ROLE_") String authorityPrefix,
        @DefaultValue List<String> publicPaths) {
}
