package br.com.manuelo.dados;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    private record UsuarioBanco(String id, String nome, String email, String senhaHash,
            String perfil, String status) {
    }

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final SessaoUsuario sessao;
    private final SecureRandom random = new SecureRandom();

    public AutenticacaoService(JdbcTemplate jdbcTemplate, PasswordEncoder encoder, SessaoUsuario sessao) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
        this.encoder = encoder;
        this.sessao = sessao;
    }

    public Resultado login(String email, String senha) {
        if (email == null || email.isBlank() || senha == null || senha.isBlank()) {
            return Resultado.INVALIDO;
        }
        Optional<UsuarioBanco> encontrado = jdbc
                .sql("""
                        SELECT id::text AS id, nome, email, senha_hash,
                               perfil::text AS perfil, status::text AS status
                          FROM usuarios
                         WHERE lower(email) = lower(:email)
                        """)
                .param("email", email.trim())
                .query((rs, row) -> new UsuarioBanco(rs.getString("id"), rs.getString("nome"),
                        rs.getString("email"), rs.getString("senha_hash"), rs.getString("perfil"),
                        rs.getString("status")))
                .list()
                .stream()
                .findFirst();

        if (encontrado.isEmpty() || !encoder.matches(senha, encontrado.get().senhaHash())) {
            return Resultado.INVALIDO;
        }

        UsuarioBanco usuario = encontrado.get();
        return switch (usuario.status()) {
            case "PENDENTE_APROVACAO" -> Resultado.PENDENTE;
            case "INATIVO" -> Resultado.INATIVO;
            default -> {
                jdbc.sql("UPDATE usuarios SET ultimo_acesso_em = now() WHERE id = :id")
                        .param("id", UUID.fromString(usuario.id()))
                        .update();
                sessao.entrar(new SessaoUsuario.UsuarioSessao(usuario.id(), usuario.nome(),
                        usuario.email(), usuario.perfil()));
                yield Resultado.OK;
            }
        };
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

    public void solicitarRecuperacao(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        List<String> ids = jdbc.sql("SELECT id::text FROM usuarios WHERE lower(email) = lower(:email)")
                .param("email", email.trim())
                .query(String.class)
                .list();
        if (ids.isEmpty()) {
            return;
        }

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String tokenHash = HexFormat.of().formatHex(bytes);
        jdbc.sql("""
                INSERT INTO tokens_redefinicao_senha (usuario_id, token_hash, expira_em)
                VALUES (:usuario, :hash, :expira)
                """)
                .param("usuario", UUID.fromString(ids.get(0)))
                .param("hash", tokenHash)
                .param("expira", OffsetDateTime.now().plusHours(1))
                .update();
        sessao.iniciarRecuperacao(email.trim());
    }

    public boolean redefinirSenha(String novaSenha) {
        String email = sessao.emailRecuperacao();
        if (email == null || novaSenha == null || novaSenha.length() < 8) {
            return false;
        }
        int alterados = jdbc.sql("""
                UPDATE usuarios SET senha_hash = :senha
                 WHERE lower(email) = lower(:email)
                """)
                .param("senha", encoder.encode(novaSenha))
                .param("email", email)
                .update();
        if (alterados == 0) {
            return false;
        }
        jdbc.sql("""
                UPDATE tokens_redefinicao_senha t
                   SET usado_em = now()
                 WHERE t.usuario_id = (SELECT id FROM usuarios WHERE lower(email) = lower(:email))
                   AND t.usado_em IS NULL
                """)
                .param("email", email)
                .update();
        sessao.encerrarRecuperacao();
        return true;
    }
}
