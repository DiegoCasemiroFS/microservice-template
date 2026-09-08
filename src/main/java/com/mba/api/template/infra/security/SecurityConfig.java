package com.mba.api.template.infra.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cadeia de segurança do template, montada exclusivamente com os mecanismos oficiais
 * do Spring Security para JWT — sem parser de token, sem filtro de autenticação e sem
 * conversor de claims escritos à mão.
 *
 * <p>Acesso não autorizado é a ameaça mais tratada na literatura de segurança de
 * microsserviços, presente em 50% dos 46 estudos mapeados por Hannousse e Yahiouche
 * (2021); ainda assim, apenas 20% das soluções atuam na camada do microsserviço
 * individual e 6,67% na composição — exatamente a camada onde {@link #enabledChain}
 * opera, negando por padrão qualquer endpoint não explicitamente liberado.
 *
 * <p>A validação de audiência do token (propriedade nativa
 * {@code spring.security.oauth2.resourceserver.jwt.audiences}) cobre ameaças internas,
 * foco de apenas 13% dos estudos mapeados por Hannousse e Yahiouche (2021), embora
 * relatórios de mercado atribuam a essa origem a maioria dos ataques efetivos: sem
 * essa checagem, um token emitido pelo mesmo provedor para outro serviço seria aceito
 * aqui.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    private static final String HEALTH_CHECK_PATH = "/actuator/health/**";

    /**
     * Cadeia ativa por padrão ({@code app.security.enabled} ausente ou {@code true}).
     *
     * <p>Nega por padrão: todo endpoint exige um JWT válido, exceto o health check e
     * os caminhos declarados em {@link SecurityProperties#publicPaths()}. A validação
     * do token (assinatura, expiração, emissor e audiência) é inteiramente delegada
     * ao {@code JwtDecoder} autoconfigurado pelo Spring Boot a partir de
     * {@code spring.security.oauth2.resourceserver.jwt.*}; nenhum {@code JwtDecoder}
     * é declarado manualmente aqui, o que evitaria acesso de rede síncrono durante a
     * subida do contexto sempre que o decoder precisar resolver um issuer remoto.
     *
     * @param http       builder de segurança fornecido pelo Spring
     * @param properties claim, prefixo e caminhos públicos configurados
     * @param handlers   respostas 401/403 no formato ProblemDetail do template
     * @return a cadeia de filtros construída
     * @throws Exception se a configuração do {@link HttpSecurity} falhar
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.security", name = "enabled", havingValue = "true", matchIfMissing = true)
    SecurityFilterChain enabledChain(HttpSecurity http, SecurityProperties properties, SecurityErrorHandlers handlers)
            throws Exception {
        return http
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(HEALTH_CHECK_PATH).permitAll();
                    if (!properties.publicPaths().isEmpty()) {
                        authorize.requestMatchers(properties.publicPaths().toArray(String[]::new)).permitAll();
                    }
                    authorize.anyRequest().authenticated();
                })
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter(properties)))
                        .authenticationEntryPoint(handlers.authenticationEntryPoint())
                        .accessDeniedHandler(handlers.accessDeniedHandler()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .build();
    }

    /**
     * Cadeia ativa apenas quando {@code app.security.enabled=false}.
     *
     * <p>Libera todos os endpoints. Existe porque, na ausência de qualquer
     * {@code SecurityFilterChain}, o Spring Boot aplicaria sua própria configuração
     * padrão — protegendo tudo com uma credencial básica gerada em log — o oposto do
     * que se espera ao desligar a camada de segurança para desenvolvimento local.
     *
     * @param http builder de segurança fornecido pelo Spring
     * @return a cadeia de filtros construída, sem autenticação
     * @throws Exception se a configuração do {@link HttpSecurity} falhar
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.security", name = "enabled", havingValue = "false")
    SecurityFilterChain disabledChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .build();
    }

    /**
     * Monta o conversor de authorities com as classes de fábrica do Spring Security
     * ({@link JwtGrantedAuthoritiesConverter} + {@link JwtAuthenticationConverter}),
     * parametrizadas pelo claim e prefixo declarados em {@link SecurityProperties}.
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter(SecurityProperties properties) {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName(properties.authoritiesClaim());
        authoritiesConverter.setAuthorityPrefix(properties.authorityPrefix());

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
