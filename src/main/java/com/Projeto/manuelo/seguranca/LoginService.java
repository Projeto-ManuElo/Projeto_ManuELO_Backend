package com.Projeto.manuelo.seguranca;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import com.Projeto.manuelo.dominio.Usuario;
import com.Projeto.manuelo.dominio.repositorio.UsuarioJpaRepository;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * A-10: login/logout HTTP reais. Autentica via AuthenticationManager (UserDetailsService +
 * BCrypt + gate por status), grava o SecurityContext na sessão HTTP corrente do Vaadin e
 * atualiza ultimo_acesso_em. A view usa o resultado para exibir a mensagem correta.
 */
@Service
public class LoginService {

    private static final Logger LOG = LoggerFactory.getLogger(LoginService.class);

    public enum Resultado {
        OK, PENDENTE, INATIVO, INVALIDO
    }

    private final AuthenticationManager authenticationManager;
    private final UsuarioJpaRepository usuarios;
    private final PasswordEncoder passwordEncoder;

    public LoginService(AuthenticationManager authenticationManager, UsuarioJpaRepository usuarios,
            PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
    }

    public Resultado login(String email, String senha) {
        if (email == null || email.isBlank() || senha == null || senha.isBlank()) {
            return Resultado.INVALIDO;
        }

        String identificador = email.trim();
        try {
            Authentication autenticado = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(identificador, senha));
            SecurityContext contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(autenticado);
            SecurityContextHolder.setContext(contexto);
            if (!salvarContextoNaSessao(contexto)) {
                SecurityContextHolder.clearContext();
                return Resultado.INVALIDO;
            }
            atualizarUltimoAcesso(autenticado.getName());
            return Resultado.OK;
        } catch (DisabledException | LockedException ex) {
            return resultadoPorStatus(identificador, senha);
        } catch (BadCredentialsException ex) {
            return Resultado.INVALIDO;
        } catch (AuthenticationException ex) {
            return Resultado.INVALIDO;
        }
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        if (request == null) {
            return;
        }
        HttpSession sessao = request.getSession(false);
        if (sessao != null) {
            // A sessão continua viva: invalidá-la durante um evento UIDL derrubaria o @Push do Vaadin.
            try {
                sessao.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            } catch (IllegalStateException ignorado) {
                // Sessão já invalidada: nada a fazer.
            }
        }
    }

    public void logoutSemInvalidar(HttpServletRequest request, HttpServletResponse response) {
        logout(request, response);
    }

    public void logout() {
        logout(VaadinRequest.getCurrent() instanceof HttpServletRequest request ? request : null,
                VaadinResponse.getCurrent() instanceof HttpServletResponse response ? response : null);
    }

    private boolean salvarContextoNaSessao(SecurityContext contexto) {
        try {
            if (!(VaadinRequest.getCurrent() instanceof HttpServletRequest request)
                    || !(VaadinResponse.getCurrent() instanceof HttpServletResponse response)) {
                LOG.warn("Contexto de segurança não persistido: requisição HTTP indisponível");
                return false;
            }
            request.getSession(true);
            request.changeSessionId();
            HttpSessionSecurityContextRepository repositorio = new HttpSessionSecurityContextRepository();
            repositorio.setSecurityContextHolderStrategy(SecurityContextHolder.getContextHolderStrategy());
            repositorio.saveContext(contexto, request, response);
            return true;
        } catch (RuntimeException ex) {
            LOG.warn("Falha ao salvar contexto de segurança na sessão: {}", ex.getClass().getSimpleName());
            return false;
        }
    }

    private void atualizarUltimoAcesso(String email) {
        try {
            usuarios.findByEmailIgnoreCase(email).ifPresent(this::registrarAcesso);
        } catch (RuntimeException ignorado) {
            // A autenticação já foi concluída; falha de auditoria não deve derrubar o login.
        }
    }

    private void registrarAcesso(Usuario usuario) {
        usuario.setUltimoAcessoEm(OffsetDateTime.now(ZoneOffset.UTC));
        usuarios.save(usuario);
    }

    private Resultado resultadoPorStatus(String email, String senha) {
        return usuarios.findByEmailIgnoreCase(email)
                .filter(usuario -> senhaConfere(senha, usuario))
                .map(Usuario::getStatus)
                .map(status -> switch (status) {
                    case PENDENTE_APROVACAO -> Resultado.PENDENTE;
                    case INATIVO -> Resultado.INATIVO;
                    default -> Resultado.INVALIDO;
                })
                .orElse(Resultado.INVALIDO);
    }

    private boolean senhaConfere(String senha, Usuario usuario) {
        String hash = usuario.getSenhaHash();
        return hash != null && !hash.isBlank() && passwordEncoder.matches(senha, hash);
    }
}