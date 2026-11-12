package com.Projeto.manuelo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import com.Projeto.manuelo.seguranca.UsuarioDetalhesService;

/**
 * A-10: autenticação HTTP real com UserDetailsService, BCrypt e gate por status.
 * As telas públicas continuam acessíveis; qualquer outra rota exige sessão autenticada
 * e, sem ela, redireciona para a tela de login em "/".
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private static final String[] ROTAS_PUBLICAS = {
			"/",
			"/cadastro",
			"/aguardando-aprovacao",
			"/aprovacao",
			"/recuperar-senha",
			"/redefinir-senha",
			"/email-enviado",
			"/VAADIN/**",
			"/frontend/**",
			"/images/**",
			"/favicon.ico",
			"/error",
			"/styles.css",
			"/aura/**",
			"/icons/**",
			"/sw.js",
			"/manifest.webmanifest",
			"/offline.html",
			"/favicon.svg"
	};

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
				http.authorizeHttpRequests(auth -> auth
						.requestMatchers(ROTAS_PUBLICAS).permitAll()
						.requestMatchers("/usuarios", "/usuarios/**").hasRole("ADMINISTRADOR")
						.anyRequest().authenticated())
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/")))
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
		return http.build();
	}

	@Bean
	AuthenticationManager authenticationManager(UsuarioDetalhesService usuarioDetalhesService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provedor = new DaoAuthenticationProvider(usuarioDetalhesService);
		provedor.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provedor);
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}