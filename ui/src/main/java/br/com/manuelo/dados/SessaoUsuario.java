package br.com.manuelo.dados;

import java.io.Serializable;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import com.vaadin.flow.spring.scopes.VaadinSessionScope;

/**
 * Usuário autenticado na sessão Vaadin. A sessão é a fronteira de autenticação do app:
 * o HTTP continua liberado para os recursos estáticos do Vaadin, mas as telas protegidas
 * passam pelo {@code MainLayout}, que redireciona para o login quando não há usuário.
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

    public boolean autenticado() {
        return usuario != null;
    }

    public UsuarioSessao usuario() {
        return usuario;
    }

    public String id() {
        return usuario == null ? null : usuario.id();
    }

    public boolean administrador() {
        return usuario != null && usuario.administrador();
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
}
