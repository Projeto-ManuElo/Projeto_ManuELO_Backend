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

    /**
     * A-98: resultado tipado do cadastro, para a view distinguir e-mail já em
     * uso de dados inválidos (relatório de bugs de frontend de 22/11/2026).
     */
    public enum Resultado {
        OK,
        EMAIL_EM_USO,
        DADOS_INVALIDOS
    }

    /**
     * A-98: mantém o contrato booleano histórico, delegando para o resultado
     * tipado. Qualquer resultado diferente de OK continua recusando o cadastro.
     */
    public boolean cadastrar(String nome, String email, String senha) {
        return cadastrarComResultado(nome, email, senha) == Resultado.OK;
    }

    /**
     * Valida nome/e-mail/senha (mínimo de {@value #TAMANHO_MINIMO_SENHA}
     * caracteres), recusa e-mail já cadastrado ignorando a caixa e grava
     * COLABORADOR/PENDENTE_APROVACAO com senha BCrypt. Dados inválidos têm
     * precedência sobre e-mail duplicado.
     */
    public Resultado cadastrarComResultado(String nome, String email, String senha) {
        if (nome == null || nome.isBlank() || email == null || email.isBlank()
                || senha == null || senha.length() < TAMANHO_MINIMO_SENHA) {
            return Resultado.DADOS_INVALIDOS;
        }
        String emailNormalizado = email.trim();
        if (usuarios.existsByEmailIgnoreCase(emailNormalizado)) {
            return Resultado.EMAIL_EM_USO;
        }

        Usuario usuario = new Usuario();
        usuario.setNome(nome.trim());
        usuario.setEmail(emailNormalizado);
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        usuario.setPerfil(PerfilUsuario.COLABORADOR);
        usuario.setStatus(StatusUsuario.PENDENTE_APROVACAO);
        usuarios.save(usuario);
        return Resultado.OK;
    }
}
