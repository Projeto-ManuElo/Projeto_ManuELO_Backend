package com.Projeto.manuelo.seguranca;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.Projeto.manuelo.dominio.Usuario;
import com.Projeto.manuelo.dominio.enums.PerfilUsuario;
import com.Projeto.manuelo.dominio.enums.StatusUsuario;

/**
 * Principal autenticado do ManuElo (A-10). O e-mail é o username; o status controla
 * o acesso: somente ATIVO é habilitado e não bloqueado (PENDENTE_APROVACAO/INATIVO
 * são barrados pelo DaoAuthenticationProvider).
 */
public class UsuarioDetalhes implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String nome;
    private final String email;
    private final String senhaHash;
    private final PerfilUsuario perfil;
    private final StatusUsuario status;

    public UsuarioDetalhes(Usuario usuario) {
        this.id = usuario.getId();
        this.nome = usuario.getNome();
        this.email = usuario.getEmail();
        this.senhaHash = usuario.getSenhaHash();
        this.perfil = usuario.getPerfil();
        this.status = usuario.getStatus();
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public PerfilUsuario getPerfil() {
        return perfil;
    }

    public StatusUsuario getStatus() {
        return status;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (perfil == null) {
            return List.of();
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + perfil.name()));
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status == StatusUsuario.ATIVO;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == StatusUsuario.ATIVO;
    }
}