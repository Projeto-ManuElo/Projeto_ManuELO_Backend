package com.Projeto.manuelo.seguranca;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.Projeto.manuelo.dominio.repositorio.UsuarioJpaRepository;

/**
 * Carrega o usuário do banco pelo e-mail (case-insensitive) para o Spring Security.
 */
@Service
public class UsuarioDetalhesService implements UserDetailsService {

    private final UsuarioJpaRepository usuarios;

    public UsuarioDetalhesService(UsuarioJpaRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException("E-mail não informado");
        }
        return usuarios.findByEmailIgnoreCase(email.trim())
                .map(UsuarioDetalhes::new)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + email));
    }
}