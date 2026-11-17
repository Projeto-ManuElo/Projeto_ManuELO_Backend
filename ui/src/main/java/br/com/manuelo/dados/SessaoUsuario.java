package br.com.manuelo.dados;

import java.io.Serializable;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.Projeto.manuelo.dominio.Usuario;
import com.Projeto.manuelo.dominio.enums.StatusUsuario;
import com.Projeto.manuelo.dominio.repositorio.UsuarioJpaRepository;
import com.Projeto.manuelo.seguranca.UsuarioDetalhes;
import com.vaadin.flow.spring.scopes.VaadinSessionScope;

/**
 * Usuário autenticado na sessão Vaadin. A sessão é a fronteira de autenticação do app:
 * o HTTP continua liberado para os recursos estáticos do Vaadin, mas as telas protegidas
 * passam pelo {@code MainLayout}, que redireciona para o login quando não há usuário.
 *
 * A-10: além de {@link #entrar}/{@link #sair}, a sessão se sincroniza sob demanda com o
 * {@code SecurityContext} do Spring (Authentication com principal {@link UsuarioDetalhes}),
 * para que o login via AuthenticationManager reflita aqui sem depender só da view.
 */
@Component
@Scope(value = VaadinSessionScope.VAADIN_SESSION_SCOPE_NAME, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class SessaoUsuario implements Serializable {

    public record UsuarioSessao(String id, String nome, String email, String perfil) implements Serializable {
        public boolean administrador() {
            return "ADMINISTRADOR".equals(perfil);
        }
    }

    private UsuarioSessao usuario;
    private String emailRecuperacao;
    private final UsuarioJpaRepository usuarios;

    public SessaoUsuario() {
        this(null);
    }

    @Autowired
    public SessaoUsuario(UsuarioJpaRepository usuarios) {
        this.usuarios = usuarios;
    }

    public boolean autenticado() {
        return usuario() != null;
    }

    public UsuarioSessao usuario() {
        if (usuario == null) {
            usuario = usuarioDoSecurityContext();
        }
        if (usuario != null) {
            usuario = revalidar(usuario);
        }
        return usuario;
    }

    public String id() {
        UsuarioSessao atual = usuario();
        return atual == null ? null : atual.id();
    }

    public boolean administrador() {
        UsuarioSessao atual = usuario();
        return atual != null && atual.administrador();
    }

    public void entrar(UsuarioSessao usuario) {
        this.usuario = usuario;
    }

    public void sair() {
        this.usuario = null;
        this.emailRecuperacao = null;
    }

    public String emailRecuperacao() {
        return emailRecuperacao;
    }

    public void iniciarRecuperacao(String email) {
        this.emailRecuperacao = email;
    }

    public void encerrarRecuperacao() {
        this.emailRecuperacao = null;
    }

    private UsuarioSessao usuarioDoSecurityContext() {
        try {
            Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
            if (autenticacao == null || !autenticacao.isAuthenticated()
                    || !(autenticacao.getPrincipal() instanceof UsuarioDetalhes detalhes)) {
                return null;
            }
            return new UsuarioSessao(
                    detalhes.getId() == null ? null : detalhes.getId().toString(),
                    detalhes.getNome(),
                    detalhes.getEmail(),
                    detalhes.getPerfil() == null ? null : detalhes.getPerfil().name());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private UsuarioSessao revalidar(UsuarioSessao atual) {
        if (usuarios == null) {
            return atual;
        }
        UUID id = idDoSecurityContext();
        if (id == null) {
            return atual;
        }
        try {
            Usuario usuarioBanco = usuarios.findById(id).orElse(null);
            if (usuarioBanco == null || usuarioBanco.getStatus() != StatusUsuario.ATIVO) {
                sair();
                return null;
            }
            return new UsuarioSessao(
                    usuarioBanco.getId() == null ? null : usuarioBanco.getId().toString(),
                    usuarioBanco.getNome(),
                    usuarioBanco.getEmail(),
                    usuarioBanco.getPerfil() == null ? null : usuarioBanco.getPerfil().name());
        } catch (RuntimeException ex) {
            return atual;
        }
    }

    private UUID idDoSecurityContext() {
        try {
            Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
            if (autenticacao == null || !autenticacao.isAuthenticated()
                    || !(autenticacao.getPrincipal() instanceof UsuarioDetalhes detalhes)) {
                return null;
            }
            return detalhes.getId();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
