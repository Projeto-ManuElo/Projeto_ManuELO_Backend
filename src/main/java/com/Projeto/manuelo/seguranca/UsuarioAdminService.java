package com.Projeto.manuelo.seguranca;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Projeto.manuelo.dominio.Usuario;
import com.Projeto.manuelo.dominio.enums.PerfilUsuario;
import com.Projeto.manuelo.dominio.enums.StatusUsuario;
import com.Projeto.manuelo.dominio.repositorio.UsuarioJpaRepository;

/**
 * A-12: gestão de usuários (listar, aprovar, inativar, alterar perfil). O ator vem do
 * SecurityContext (nunca por parâmetro) e as gravações passam pela auditoria JPA.
 */
@Service
public class UsuarioAdminService {

    private static final Comparator<UsuarioLista> ORDEM = Comparator
            .comparingInt((UsuarioLista usuario) -> peso(usuario.status()))
            .thenComparing(UsuarioLista::nome, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

    public record UsuarioLista(String id, String nome, String email, String perfil, String status) {
    }

    private final UsuarioJpaRepository usuarios;

    public UsuarioAdminService(UsuarioJpaRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
    public List<UsuarioLista> listar() {
        atorAdmin();
        return usuarios.findAll().stream()
                .map(this::paraLista)
                .sorted(ORDEM)
                .toList();
    }

    @Transactional
    public void aprovar(UUID id) {
        Usuario usuario = buscar(id);
        if (usuario.getStatus() != StatusUsuario.PENDENTE_APROVACAO) {
            throw new IllegalStateException(
                    "Só é possível aprovar usuários com status PENDENTE_APROVACAO.");
        }
        UUID ator = atorAdmin().getId();
        usuario.setStatus(StatusUsuario.ATIVO);
        usuario.setAprovadoPor(ator);
        usuario.setAprovadoEm(OffsetDateTime.now(ZoneOffset.UTC));
        usuarios.save(usuario);
    }

    @Transactional
    public void inativar(UUID id) {
        UUID ator = atorAdmin().getId();
        if (ator.equals(id)) {
            throw new IllegalStateException("Um usuário não pode inativar a si mesmo.");
        }
        Usuario usuario = buscar(id);
        usuario.setStatus(StatusUsuario.INATIVO);
        usuarios.save(usuario);
    }

    @Transactional
    public void alterarPerfil(UUID id, PerfilUsuario perfil) {
        if (perfil == null) {
            throw new IllegalArgumentException("Perfil é obrigatório.");
        }
        atorAdmin();
        Usuario usuario = buscar(id);
        usuario.setPerfil(perfil);
        usuarios.save(usuario);
    }

    private Usuario buscar(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Identificador do usuário é obrigatório.");
        }
        return usuarios.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + id));
    }

    private UsuarioDetalhes ator() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !(autenticacao.getPrincipal() instanceof UsuarioDetalhes detalhes)
                || detalhes.getId() == null) {
            throw new IllegalStateException("Usuário autenticado obrigatório");
        }
        return detalhes;
    }

    private UsuarioDetalhes atorAdmin() {
        UsuarioDetalhes detalhes = ator();
        if (detalhes.getPerfil() != PerfilUsuario.ADMINISTRADOR) {
            throw new AccessDeniedException("Apenas administradores podem gerenciar usuários.");
        }
        return detalhes;
    }

    private UsuarioLista paraLista(Usuario usuario) {
        return new UsuarioLista(
                usuario.getId() == null ? null : usuario.getId().toString(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getPerfil() == null ? null : usuario.getPerfil().name(),
                usuario.getStatus() == null ? null : usuario.getStatus().name());
    }

    private static int peso(String status) {
        if (StatusUsuario.PENDENTE_APROVACAO.name().equals(status)) {
            return 0;
        }
        if (StatusUsuario.ATIVO.name().equals(status)) {
            return 1;
        }
        if (StatusUsuario.INATIVO.name().equals(status)) {
            return 2;
        }
        return 3;
    }
}
