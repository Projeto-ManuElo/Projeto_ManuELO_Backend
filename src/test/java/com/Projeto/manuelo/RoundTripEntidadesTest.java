package com.Projeto.manuelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.Projeto.manuelo.dominio.CaucaoMovimentacao;
import com.Projeto.manuelo.dominio.Configuracao;
import com.Projeto.manuelo.dominio.Contrato;
import com.Projeto.manuelo.dominio.Documento;
import com.Projeto.manuelo.dominio.Fiador;
import com.Projeto.manuelo.dominio.Imovel;
import com.Projeto.manuelo.dominio.Inquilino;
import com.Projeto.manuelo.dominio.Movimentacao;
import com.Projeto.manuelo.dominio.Reforma;
import com.Projeto.manuelo.dominio.Relatorio;
import com.Projeto.manuelo.dominio.RelatorioContrato;
import com.Projeto.manuelo.dominio.RelatorioContratoId;
import com.Projeto.manuelo.dominio.RelatorioMovimentacao;
import com.Projeto.manuelo.dominio.RelatorioMovimentacaoId;
import com.Projeto.manuelo.dominio.TokenRedefinicaoSenha;
import com.Projeto.manuelo.dominio.Usuario;
import com.Projeto.manuelo.dominio.enums.CategoriaMovimentacao;
import com.Projeto.manuelo.dominio.enums.FormaPagamento;
import com.Projeto.manuelo.dominio.enums.PerfilUsuario;
import com.Projeto.manuelo.dominio.enums.SituacaoRelatorioContrato;
import com.Projeto.manuelo.dominio.enums.StatusImovel;
import com.Projeto.manuelo.dominio.enums.StatusUsuario;
import com.Projeto.manuelo.dominio.enums.TipoCaucao;
import com.Projeto.manuelo.dominio.enums.TipoDocumento;
import com.Projeto.manuelo.dominio.enums.TipoImovel;
import com.Projeto.manuelo.dominio.enums.TipoMovimentacao;
import com.Projeto.manuelo.dominio.enums.TipoRelatorio;
import com.Projeto.manuelo.dominio.repositorio.CaucaoMovimentacaoJpaRepository;
import com.Projeto.manuelo.dominio.repositorio.ConfiguracaoJpaRepository;
import com.Projeto.manuelo.dominio.repositorio.ContratoJpaRepository;
import com.Projeto.manuelo.dominio.repositorio.DocumentoJpaRepository;
import com.Projeto.manuelo.dominio.repositorio.FiadorJpaRepository;
import com.Projeto.manuelo.dominio.repositorio.ImovelJpaRepository;
import com.Projeto.manuelo.dominio.repositorio.InquilinoJpaRepository;
import com.Projeto.manuelo.dominio.repositorio.MovimentacaoJpaRepository;
import com.Projeto.manuelo.dominio.repositorio.ReformaJpaRepository;
import com.Projeto.manuelo.dominio.repositorio.RelatorioContratoJpaRepository;
import com.Projeto.manuelo.dominio.repositorio.RelatorioJpaRepository;
import com.Projeto.manuelo.dominio.repositorio.RelatorioMovimentacaoJpaRepository;
import com.Projeto.manuelo.dominio.repositorio.TokenRedefinicaoSenhaJpaRepository;
import com.Projeto.manuelo.dominio.repositorio.UsuarioJpaRepository;

import jakarta.persistence.EntityManager;

/**
 * A-03: prova de round-trip (insert + select) das 14 entidades do modelo v3
 * usando JPA/Spring Data contra o Postgres real, sem mocks.
 *
 * Rollback ao fim de cada teste. A tentativa de UPDATE em movimentacoes roda em
 * transacao propria (REQUIRES_NEW) para receber a excecao da trigger de imutabilidade
 * sem envenenar a transacao principal.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RoundTripEntidadesTest {

    @Autowired
    private UsuarioJpaRepository usuarioRepo;
    @Autowired
    private TokenRedefinicaoSenhaJpaRepository tokenRepo;
    @Autowired
    private ImovelJpaRepository imovelRepo;
    @Autowired
    private InquilinoJpaRepository inquilinoRepo;
    @Autowired
    private FiadorJpaRepository fiadorRepo;
    @Autowired
    private ContratoJpaRepository contratoRepo;
    @Autowired
    private CaucaoMovimentacaoJpaRepository caucaoRepo;
    @Autowired
    private MovimentacaoJpaRepository movimentacaoRepo;
    @Autowired
    private DocumentoJpaRepository documentoRepo;
    @Autowired
    private ReformaJpaRepository reformaRepo;
    @Autowired
    private RelatorioJpaRepository relatorioRepo;
    @Autowired
    private RelatorioMovimentacaoJpaRepository relatorioMovimentacaoRepo;
    @Autowired
    private RelatorioContratoJpaRepository relatorioContratoRepo;
    @Autowired
    private ConfiguracaoJpaRepository configuracaoRepo;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void limparSeguranca() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void roundTripDasQuatorzeEntidades() {
        String sufixo = UUID.randomUUID().toString();

        // 1. usuario (dependencia de todas as FKs de autoria)
        Usuario usuario = new Usuario();
        usuario.setNome("QA " + sufixo);
        usuario.setEmail("qa." + sufixo + "@manuelo.local");
        usuario.setSenhaHash("hash-qa-" + sufixo + "-com-tamanho-suficiente");
        usuario.setPerfil(PerfilUsuario.COLABORADOR);
        usuario.setStatus(StatusUsuario.PENDENTE_APROVACAO);
        usuario = usuarioRepo.save(usuario);
        UUID usuarioId = usuario.getId();
        assertThat(usuarioId).as("UUID gerado para usuarios").isNotNull();

        autenticarComo(usuarioId);

        // 2. token de redefinicao de senha
        TokenRedefinicaoSenha token = new TokenRedefinicaoSenha();
        token.setUsuarioId(usuarioId);
        token.setTokenHash(UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", ""));
        token.setExpiraEm(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
        token = tokenRepo.save(token);
        UUID tokenId = token.getId();
        assertThat(tokenId).isNotNull();

        // 3. imovel
        Imovel imovel = new Imovel();
        imovel.setLogradouro("Rua QA " + sufixo);
        imovel.setNumero("100");
        imovel.setBairro("Centro");
        imovel.setCidade("Aracaju");
        imovel.setUf("SE");
        imovel.setCep("49000-000");
        imovel.setTipo(TipoImovel.CASA);
        imovel.setStatus(StatusImovel.VAGO);
        imovel = imovelRepo.save(imovel);
        UUID imovelId = imovel.getId();
        assertThat(imovelId).isNotNull();

        // 4. inquilino
        Inquilino inquilino = new Inquilino();
        inquilino.setNome("Inquilino QA " + sufixo);
        inquilino.setDocumento(documento(11));
        inquilino.setEmail("inquilino." + sufixo + "@manuelo.local");
        inquilino.setTelefone("79999990000");
        inquilino = inquilinoRepo.save(inquilino);
        UUID inquilinoId = inquilino.getId();
        assertThat(inquilinoId).isNotNull();

        // 5. fiador
        Fiador fiador = new Fiador();
        fiador.setNome("Fiador QA " + sufixo);
        fiador.setDocumento(documento(14));
        fiador.setEmail("fiador." + sufixo + "@manuelo.local");
        fiador.setTelefone("79999991111");
        fiador = fiadorRepo.save(fiador);
        UUID fiadorId = fiador.getId();
        assertThat(fiadorId).isNotNull();

        // 6. contrato
        Contrato contrato = new Contrato();
        contrato.setImovelId(imovelId);
        contrato.setInquilinoId(inquilinoId);
        contrato.setFiadorId(fiadorId);
        contrato.setInicio(LocalDate.now());
        contrato.setValorAluguel(new BigDecimal("1200.00"));
        contrato.setDiaVencimento((short) 5);
        contrato.setAcompanharPagamentoDesde(LocalDate.now());
        contrato.setPossuiAcaoJudicial(false);
        contrato = contratoRepo.save(contrato);
        UUID contratoId = contrato.getId();
        assertThat(contratoId).isNotNull();

        // 7. movimentacao (imutavel; campos so por reflexao, sem setter)
        Movimentacao movimentacao = new Movimentacao();
        definir(movimentacao, "tipo", TipoMovimentacao.ENTRADA);
        definir(movimentacao, "categoria", CategoriaMovimentacao.OUTRAS_ENTRADAS);
        definir(movimentacao, "valor", new BigDecimal("150.00"));
        definir(movimentacao, "dataMovimentacao", LocalDate.now());
        definir(movimentacao, "descricao", "Entrada QA " + sufixo);
        definir(movimentacao, "formaPagamento", FormaPagamento.PIX);
        definir(movimentacao, "responsavelId", usuarioId);
        definir(movimentacao, "origemDescricao", "Origem QA " + sufixo);
        movimentacao = movimentacaoRepo.save(movimentacao);
        UUID movimentacaoId = movimentacao.getId();
        assertThat(movimentacaoId).isNotNull();

        // 8. caucao_movimentacao (imutavel)
        CaucaoMovimentacao caucao = new CaucaoMovimentacao();
        definir(caucao, "contratoId", contratoId);
        definir(caucao, "tipo", TipoCaucao.DEPOSITO);
        definir(caucao, "valor", new BigDecimal("1200.00"));
        definir(caucao, "data", LocalDate.now());
        definir(caucao, "criadoPor", usuarioId);
        caucao = caucaoRepo.save(caucao);
        UUID caucaoId = caucao.getId();
        assertThat(caucaoId).isNotNull();

        // 9. documento
        Documento documento = new Documento();
        documento.setNomeArquivo("comprovante-" + sufixo + ".pdf");
        documento.setTipo(TipoDocumento.COMPROVANTE);
        documento.setDataDocumento(LocalDate.now());
        documento.setPendenteRevisao(false);
        documento.setCaminhoArmazenamento("qa/" + sufixo + ".pdf");
        documento.setTipoMime("application/pdf");
        documento.setTamanhoBytes(2048L);
        documento.setMovimentacaoId(movimentacaoId);
        documento.setEnviadoPor(usuarioId);
        documento = documentoRepo.save(documento);
        UUID documentoId = documento.getId();
        assertThat(documentoId).isNotNull();

        // 10. reforma
        Reforma reforma = new Reforma();
        reforma.setImovelId(imovelId);
        reforma.setDataReforma(LocalDate.now());
        reforma.setDescricao("Reforma QA " + sufixo);
        reforma.setValor(new BigDecimal("300.00"));
        reforma.setMovimentacaoId(movimentacaoId);
        reforma = reformaRepo.save(reforma);
        UUID reformaId = reforma.getId();
        assertThat(reformaId).isNotNull();

        // 11. relatorio (imutavel, insert-only)
        Relatorio relatorio = new Relatorio();
        definir(relatorio, "nome", "Relatorio QA " + sufixo);
        definir(relatorio, "tipo", TipoRelatorio.CONTRATOS_EXISTENTES);
        definir(relatorio, "dataReferencia", LocalDate.now());
        definir(relatorio, "qtdRegistros", 0);
        definir(relatorio, "caminhoArquivo", "qa/relatorio-" + sufixo + ".pdf");
        definir(relatorio, "geradoPor", usuarioId);
        relatorio = relatorioRepo.save(relatorio);
        UUID relatorioId = relatorio.getId();
        assertThat(relatorioId).isNotNull();

        // 12. relatorio_movimentacao (id composto; persist direto evita merge)
        RelatorioMovimentacaoId relMovId = new RelatorioMovimentacaoId(relatorioId, movimentacaoId);
        RelatorioMovimentacao relMov = new RelatorioMovimentacao();
        definir(relMov, "id", relMovId);
        definir(relMov, "ordem", 1);
        entityManager.persist(relMov);

        // 13. relatorio_contrato (id composto)
        RelatorioContratoId relContratoId = new RelatorioContratoId(relatorioId, contratoId);
        RelatorioContrato relContrato = new RelatorioContrato();
        definir(relContrato, "id", relContratoId);
        definir(relContrato, "ordem", 1);
        definir(relContrato, "situacao", SituacaoRelatorioContrato.EXISTENTE);
        entityManager.persist(relContrato);

        // 14. configuracao (PK string atribuida; persist direto evita merge)
        String chave = "qa." + sufixo;
        Configuracao configuracao = new Configuracao();
        configuracao.setChave(chave);
        configuracao.setValor("1");
        configuracao.setDescricao("Configuracao QA " + sufixo);
        entityManager.persist(configuracao);

        entityManager.flush();
        entityManager.clear();

        // --- SELECT por repositorio, sem cache de primeiro nivel ---
        Usuario usuarioLido = usuarioRepo.findById(usuarioId).orElseThrow();
        assertThat(usuarioLido.getId()).isEqualTo(usuarioId);
        assertThat(usuarioLido.getEmail()).isEqualTo("qa." + sufixo + "@manuelo.local");
        assertThat(usuarioLido.getPerfil()).isEqualTo(PerfilUsuario.COLABORADOR);
        assertThat(usuarioLido.getStatus()).isEqualTo(StatusUsuario.PENDENTE_APROVACAO);
        assertThat(usuarioLido.getCriadoEm()).as("OffsetDateTime de criado_em").isNotNull();

        TokenRedefinicaoSenha tokenLido = tokenRepo.findById(tokenId).orElseThrow();
        assertThat(tokenLido.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(tokenLido.getExpiraEm()).isNotNull();
        assertThat(tokenLido.getCriadoEm()).isNotNull();

        Imovel imovelLido = imovelRepo.findById(imovelId).orElseThrow();
        assertThat(imovelLido.getUf()).isEqualTo("SE");
        assertThat(imovelLido.getTipo()).isEqualTo(TipoImovel.CASA);
        assertThat(imovelLido.getStatus()).isEqualTo(StatusImovel.VAGO);
        assertThat(imovelLido.getCriadoEm()).isNotNull();

        Inquilino inquilinoLido = inquilinoRepo.findById(inquilinoId).orElseThrow();
        assertThat(inquilinoLido.getDocumento()).isEqualTo(inquilino.getDocumento());
        assertThat(inquilinoLido.getTelefone()).isEqualTo("79999990000");

        Fiador fiadorLido = fiadorRepo.findById(fiadorId).orElseThrow();
        assertThat(fiadorLido.getDocumento()).isEqualTo(fiador.getDocumento());
        assertThat(fiadorLido.getEmail()).isEqualTo(fiador.getEmail());

        Contrato contratoLido = contratoRepo.findById(contratoId).orElseThrow();
        assertThat(contratoLido.getValorAluguel()).isEqualByComparingTo("1200.00");
        assertThat(contratoLido.getImovelId()).isEqualTo(imovelId);
        assertThat(contratoLido.getCriadoEm()).isNotNull();

        CaucaoMovimentacao caucaoLida = caucaoRepo.findById(caucaoId).orElseThrow();
        assertThat(caucaoLida.getTipo()).isEqualTo(TipoCaucao.DEPOSITO);
        assertThat(caucaoLida.getValor()).isEqualByComparingTo("1200.00");
        assertThat(caucaoLida.getCriadoEm()).isNotNull();

        Movimentacao movimentacaoLida = movimentacaoRepo.findById(movimentacaoId).orElseThrow();
        assertThat(movimentacaoLida.getId()).isEqualTo(movimentacaoId);
        assertThat(movimentacaoLida.getTipo()).isEqualTo(TipoMovimentacao.ENTRADA);
        assertThat(movimentacaoLida.getCategoria()).isEqualTo(CategoriaMovimentacao.OUTRAS_ENTRADAS);
        assertThat(movimentacaoLida.getValor()).isEqualByComparingTo("150.00");
        assertThat(movimentacaoLida.getCriadoEm()).as("OffsetDateTime de criado_em").isNotNull();
        assertThat(Movimentacao.class.isAnnotationPresent(Immutable.class))
                .as("movimentacoes mapeada como @Immutable")
                .isTrue();
        assertThat(CaucaoMovimentacao.class.isAnnotationPresent(Immutable.class)).isTrue();
        assertThat(Relatorio.class.isAnnotationPresent(Immutable.class)).isTrue();

        Documento documentoLido = documentoRepo.findById(documentoId).orElseThrow();
        assertThat(documentoLido.getTamanhoBytes()).isEqualTo(2048L);
        assertThat(documentoLido.getMovimentacaoId()).isEqualTo(movimentacaoId);
        assertThat(documentoLido.getEnviadoEm()).isNotNull();

        Reforma reformaLida = reformaRepo.findById(reformaId).orElseThrow();
        assertThat(reformaLida.getValor()).isEqualByComparingTo("300.00");
        assertThat(reformaLida.getImovelId()).isEqualTo(imovelId);

        Relatorio relatorioLido = relatorioRepo.findById(relatorioId).orElseThrow();
        assertThat(relatorioLido.getTipo()).isEqualTo(TipoRelatorio.CONTRATOS_EXISTENTES);
        assertThat(relatorioLido.getQtdRegistros()).isZero();
        assertThat(relatorioLido.getGeradoEm()).isNotNull();

        RelatorioMovimentacao relMovLido = relatorioMovimentacaoRepo.findById(relMovId).orElseThrow();
        assertThat(relMovLido.getId()).isEqualTo(relMovId);
        assertThat(relMovLido.getOrdem()).isEqualTo(1);

        RelatorioContrato relContratoLido = relatorioContratoRepo.findById(relContratoId).orElseThrow();
        assertThat(relContratoLido.getId()).isEqualTo(relContratoId);
        assertThat(relContratoLido.getSituacao()).isEqualTo(SituacaoRelatorioContrato.EXISTENTE);

        Configuracao configuracaoLida = configuracaoRepo.findById(chave).orElseThrow();
        assertThat(configuracaoLida.getChave()).isEqualTo(chave);
        assertThat(configuracaoLida.getValor()).isEqualTo("1");
        assertThat(configuracaoLida.getCriadoEm()).isNotNull();
        assertThat(configuracaoLida.getAtualizadoEm()).isNotNull();
    }

    @Test
    void movimentacaoNaoAceitaUpdateNoBanco() {
        UUID responsavelId = usuarioComitado();

        TransactionTemplate transacaoSeparada = new TransactionTemplate(transactionManager);
        transacaoSeparada.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Throwable erro = catchThrowable(() -> transacaoSeparada.executeWithoutResult(status -> {
            Movimentacao movimentacao = new Movimentacao();
            definir(movimentacao, "tipo", TipoMovimentacao.ENTRADA);
            definir(movimentacao, "categoria", CategoriaMovimentacao.OUTRAS_ENTRADAS);
            definir(movimentacao, "valor", new BigDecimal("99.00"));
            definir(movimentacao, "dataMovimentacao", LocalDate.now());
            definir(movimentacao, "descricao", "Movimentacao imutavel QA " + UUID.randomUUID());
            definir(movimentacao, "formaPagamento", FormaPagamento.PIX);
            definir(movimentacao, "responsavelId", responsavelId);
            definir(movimentacao, "origemDescricao", "Origem QA " + UUID.randomUUID());
            movimentacao = movimentacaoRepo.save(movimentacao);
            entityManager.flush();

            UUID id = movimentacao.getId();
            assertThat(id).isNotNull();

            jdbcTemplate.update("UPDATE movimentacoes SET descricao = ? WHERE id = ?",
                    "tentativa-qa", id);
        }));

        assertThat(erro)
                .as("UPDATE em movimentacoes deve ser bloqueado pela trigger de imutabilidade")
                .isInstanceOf(DataAccessException.class);
        assertThat(erro.getMessage()).containsIgnoringCase("imut");
    }

    private void autenticarComo(UUID usuarioId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuarioId, null, List.of()));
    }

    private UUID usuarioComitado() {
        List<Usuario> existentes = usuarioRepo.findAll();
        if (!existentes.isEmpty()) {
            return existentes.get(0).getId();
        }

        TransactionTemplate transacaoSeparada = new TransactionTemplate(transactionManager);
        transacaoSeparada.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        UUID criado = transacaoSeparada.execute(status -> {
            Usuario usuario = new Usuario();
            usuario.setNome("QA imutabilidade");
            usuario.setEmail("qa.imutabilidade." + UUID.randomUUID() + "@manuelo.local");
            usuario.setSenhaHash("hash-qa-imutabilidade-com-tamanho-suficiente");
            usuario.setPerfil(PerfilUsuario.COLABORADOR);
            usuario.setStatus(StatusUsuario.PENDENTE_APROVACAO);
            return usuarioRepo.save(usuario).getId();
        });
        assertThat(criado).isNotNull();
        return criado;
    }

    private static String documento(int quantidadeDigitos) {
        long limite = 1;
        for (int i = 0; i < quantidadeDigitos; i++) {
            limite *= 10;
        }
        long valor = ThreadLocalRandom.current().nextLong(limite);
        return String.format("%0" + quantidadeDigitos + "d", valor);
    }

    private static void definir(Object alvo, String nomeCampo, Object valor) {
        Class<?> tipo = alvo.getClass();
        while (tipo != null) {
            try {
                Field campo = tipo.getDeclaredField(nomeCampo);
                campo.setAccessible(true);
                campo.set(alvo, valor);
                return;
            } catch (NoSuchFieldException e) {
                tipo = tipo.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Nao foi possivel definir " + nomeCampo, e);
            }
        }
        throw new IllegalArgumentException("Campo inexistente: " + nomeCampo);
    }
}
