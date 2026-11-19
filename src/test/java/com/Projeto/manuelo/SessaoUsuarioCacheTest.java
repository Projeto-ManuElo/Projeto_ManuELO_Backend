package com.Projeto.manuelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.Projeto.manuelo.dominio.Usuario;
import com.Projeto.manuelo.dominio.enums.PerfilUsuario;
import com.Projeto.manuelo.dominio.enums.StatusUsuario;
import com.Projeto.manuelo.dominio.repositorio.UsuarioJpaRepository;
import com.Projeto.manuelo.seguranca.UsuarioDetalhes;

import br.com.manuelo.dados.SessaoUsuario;

/**
 * A-91/DEC-I16: a navegação não pode revalidar o usuário no banco em cada chamada.
 * O teste fica vermelho no código antigo (findById por chamada) e verde com o cache
 * de 30 s de SessaoUsuario.
 */
class SessaoUsuarioCacheTest {

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void revalidaNoMaximoUmaVezEmChamadasSeguidas() {
        Usuario usuario = usuario();
        UsuarioJpaRepository repositorio = mock(UsuarioJpaRepository.class);
        when(repositorio.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        autenticar(usuario);

        SessaoUsuario sessao = new SessaoUsuario(repositorio);

        assertThat(sessao.usuario()).isNotNull();
        assertThat(sessao.usuario()).isNotNull();
        assertThat(sessao.autenticado()).isTrue();
        assertThat(sessao.administrador()).isTrue();

        verify(repositorio, times(1)).findById(usuario.getId());
    }

    @Test
    void sairInvalidaOCacheDeRevalidacao() {
        Usuario usuario = usuario();
        UsuarioJpaRepository repositorio = mock(UsuarioJpaRepository.class);
        when(repositorio.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        autenticar(usuario);

        SessaoUsuario sessao = new SessaoUsuario(repositorio);
        sessao.usuario();
        sessao.usuario();

        sessao.sair();
        assertThat(sessao.usuario()).isNotNull();

        verify(repositorio, times(2)).findById(usuario.getId());
    }

    private static void autenticar(Usuario usuario) {
        UsuarioDetalhes detalhes = new UsuarioDetalhes(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(detalhes, null,
                        detalhes.getAuthorities()));
    }

    private static Usuario usuario() {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setNome("Administrador Teste");
        usuario.setEmail("admin.teste@manuelo.local");
        usuario.setSenhaHash("$2a$10$hash-de-teste");
        usuario.setPerfil(PerfilUsuario.ADMINISTRADOR);
        usuario.setStatus(StatusUsuario.ATIVO);
        return usuario;
    }
}
