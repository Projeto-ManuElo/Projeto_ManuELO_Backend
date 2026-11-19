package com.Projeto.manuelo.seguranca;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Projeto.manuelo.dominio.Usuario;
import com.Projeto.manuelo.dominio.enums.PerfilUsuario;
import com.Projeto.manuelo.dominio.enums.StatusUsuario;
import com.Projeto.manuelo.dominio.repositorio.UsuarioJpaRepository;

/**
 * A-11/P-021: cadastro público de usuário via JPA. O novo usuário nasce
 * COLABORADOR/PENDENTE_APROVACAO, com senha BCrypt e e-mail único
 * case-insensitive. A gravação é transacional e não usa JDBC direto.
 */
@Service
@Transactional
public class CadastroUsuarioService {

    private static final int TAMANHO_MINIMO_SENHA = 8;

    private final UsuarioJpaRepository usuarios;
    private final PasswordEncoder passwordEncoder;

    public CadastroUsuarioService(UsuarioJpaRepository usuarios,
            PasswordEncoder passwordEncoder) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean cadastrar(String nome, String email, String senha) {
        if (nome == null || nome.isBlank() || email == null || email.isBlank()
                || senha == null || senha.length() < TAMANHO_MINIMO_SENHA) {
            return false;
        }
        String emailNormalizado = email.trim();
        if (usuarios.existsByEmailIgnoreCase(emailNormalizado)) {
            return false;
        }

        Usuario usuario = new Usuario();
        usuario.setNome(nome.trim());
        usuario.setEmail(emailNormalizado);
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        usuario.setPerfil(PerfilUsuario.COLABORADOR);
        usuario.setStatus(StatusUsuario.PENDENTE_APROVACAO);
        usuarios.save(usuario);
        return true;
    }
}
