package br.com.manuelo.dados;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UsuarioRepositorio {

    public record UsuarioLista(String id, String nome, String email, String perfil, String status) {
        public boolean pendente() {
            return "PENDENTE_APROVACAO".equals(status);
        }
    }

    private final JdbcClient jdbc;
    private final SessaoUsuario sessao;

    public UsuarioRepositorio(JdbcTemplate jdbcTemplate, SessaoUsuario sessao) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
        this.sessao = sessao;
    }

    public List<UsuarioLista> listar() {
        return jdbc.sql("""
                SELECT id::text AS id, nome, email, perfil::text AS perfil, status::text AS status
                  FROM usuarios
                 ORDER BY CASE status WHEN 'PENDENTE_APROVACAO' THEN 0 WHEN 'ATIVO' THEN 1 ELSE 2 END,
                          nome
                """)
                .query((rs, row) -> new UsuarioLista(rs.getString("id"), rs.getString("nome"),
                        rs.getString("email"), rs.getString("perfil"), rs.getString("status")))
                .list();
    }

    public void aprovar(String id) {
        UUID aprovador = UUID.fromString(sessao.id());
        jdbc.sql("""
                UPDATE usuarios
                   SET status = 'ATIVO', aprovado_por = :aprovador, aprovado_em = now(),
                       atualizado_por = :aprovador
                 WHERE id = :id AND status = 'PENDENTE_APROVACAO'
                """)
                .param("aprovador", aprovador)
                .param("id", UUID.fromString(id))
                .update();
    }

    public void inativar(String id) {
        UUID aprovador = UUID.fromString(sessao.id());
        jdbc.sql("""
                UPDATE usuarios
                   SET status = 'INATIVO', atualizado_por = :aprovador
                 WHERE id = :id AND id <> :aprovador
                """)
                .param("aprovador", aprovador)
                .param("id", UUID.fromString(id))
                .update();
    }
}
