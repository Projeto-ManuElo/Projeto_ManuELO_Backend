package br.com.manuelo.dados;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AutenticacaoService {

    public enum Resultado {
        OK, PENDENTE, INATIVO, INVALIDO
    }

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;

    public AutenticacaoService(JdbcTemplate jdbcTemplate, PasswordEncoder encoder) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
        this.encoder = encoder;
    }

    public boolean cadastrar(String nome, String email, String senha) {
        if (nome == null || nome.isBlank() || email == null || email.isBlank() || senha == null
                || senha.length() < 8) {
            return false;
        }
        try {
            jdbc.sql("""
                    INSERT INTO usuarios (nome, email, senha_hash, perfil, status)
                    VALUES (:nome, :email, :senha, 'COLABORADOR', 'PENDENTE_APROVACAO')
                    """)
                    .param("nome", nome.trim())
                    .param("email", email.trim())
                    .param("senha", encoder.encode(senha))
                    .update();
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }
}
