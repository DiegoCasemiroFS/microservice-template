package com.mba.api.template.infra.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de prova usados apenas por {@link SecurityIntegrationTest}, para exercitar
 * autenticação e autorização por authority sem depender de um controller de negócio.
 *
 * <p>A classe acumula {@code @TestConfiguration} (por isso precisa de {@code @Import}
 * explícito no teste, em vez de ser descoberta por component scan) e
 * {@code @RestController} (o que basta para o Spring MVC mapear seus métodos) no mesmo
 * tipo. Um controller aninhado à parte, registrado por um {@code @Bean} próprio, faria
 * o Spring registrar o mesmo handler duas vezes — uma pelo {@code @Bean}, outra porque
 * o processamento de classes de configuração também varre suas classes-membro em busca
 * de componentes — mapeando a mesma rota duas vezes e derrubando a subida do contexto
 * com "Ambiguous mapping".
 */
@TestConfiguration
@RestController
public class TestSecuredController {

    @GetMapping("/secured/ping")
    String securedPing() {
        return "pong";
    }

    @GetMapping("/public/ping")
    String publicPing() {
        return "pong";
    }

    @PreAuthorize("hasAuthority('ROLE_admin')")
    @GetMapping("/secured/admin")
    String adminPing() {
        return "pong";
    }
}
