package com.Projeto.manuelo.servico.consulta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.manuelo.Modelos;
import br.com.manuelo.dados.ContratoRepositorio;
import br.com.manuelo.dados.DocumentoRepositorio;
import br.com.manuelo.dados.HomeRepositorio;
import br.com.manuelo.dados.ImovelRepositorio;
import br.com.manuelo.dados.InquilinoRepositorio;
import br.com.manuelo.dados.MovimentacaoRepositorio;
import br.com.manuelo.dados.ReformaRepositorio;

@Service
@Transactional(readOnly = true)
public class ConsultaService {

    public record Indicadores(int cadastrados, int alugados, int vagos, int manutencao, int aVencer,
            int atrasados, int atrasadosCriticos) {
    }

    public record ImovelOpcao(String id, String rotulo) {
    }

    public record ImovelDados(String id, String logradouro, String numero, String complemento,
            String bairro, String cidade, String uf, String cep, String tipo, String status) {
    }

    public record ContratoOpcao(String id, String rotulo) {
    }

    public record ContratoDetalhe(String id, String inquilino, String documento, String email,
            String endereco, String situacao, BigDecimal aluguel, LocalDate inicio, LocalDate fim,
            int diaVencimento, boolean acaoJudicial, String acaoJudicialDescricao,
            String fiadorNome, String fiadorDocumento, String fiadorEmail, String fiadorTelefone,
            BigDecimal caucaoDepositos, BigDecimal caucaoSaldo, String imovelId) {
    }

    public record InquilinoDadosEdicao(String imovelId, BigDecimal aluguel, int diaVencimento,
            String nomeFiador, String documentoFiador, String emailFiador, BigDecimal caucao) {
    }

    public record DocumentoOpcao(String tipo, String id, String rotulo) {
    }

    public record DocumentoLista(String id, String nomeArquivo, String nomeInquilino, String tipo,
            boolean pendente, String meta, String financeiro, LocalDate data) {
    }

    public record DocumentoVinculo(String id, String nomeArquivo, String tipo, boolean pendente) {
    }

    public record ReformaImovel(String id, String endereco, String proprietario) {
    }

    private final HomeRepositorio home;
    private final MovimentacaoRepositorio movimentacoes;
    private final ImovelRepositorio imoveis;
    private final ContratoRepositorio contratos;
    private final InquilinoRepositorio inquilinos;
    private final DocumentoRepositorio documentos;
    private final ReformaRepositorio reformas;

    public ConsultaService(HomeRepositorio home, MovimentacaoRepositorio movimentacoes,
            ImovelRepositorio imoveis, ContratoRepositorio contratos,
            InquilinoRepositorio inquilinos, DocumentoRepositorio documentos,
            ReformaRepositorio reformas) {
        this.home = home;
        this.movimentacoes = movimentacoes;
        this.imoveis = imoveis;
        this.contratos = contratos;
        this.inquilinos = inquilinos;
        this.documentos = documentos;
        this.reformas = reformas;
    }

    public Indicadores indicadores() {
        HomeRepositorio.Indicadores i = home.indicadores();
        return new Indicadores(i.cadastrados(), i.alugados(), i.vagos(), i.manutencao(),
                i.aVencer(), i.atrasados(), i.atrasadosCriticos());
    }

    public List<Modelos.Movimentacao> movimentacoesRecentes(int limite) {
        return movimentacoes.listar(limite);
    }

    public List<Modelos.Imovel> listarImoveis(String endereco, String tipo, String status,
            String inquilino, int limite) {
        return imoveis.listar(endereco, tipo, status, inquilino, limite);
    }

    public List<ImovelOpcao> imoveisOpcoes() {
        return imoveis.opcoes().stream()
                .map(opcao -> new ImovelOpcao(opcao.id(), opcao.rotulo()))
                .toList();
    }

    @Transactional
    public String salvarImovel(ImovelDados dados) {
        return imoveis.salvar(new ImovelRepositorio.Dados(dados.id(), dados.logradouro(),
                dados.numero(), dados.complemento(), dados.bairro(), dados.cidade(), dados.uf(),
                dados.cep(), dados.tipo(), dados.status()));
    }

    public static String[] separarEndereco(String endereco) {
        return ImovelRepositorio.separarEndereco(endereco);
    }

    public List<Modelos.Contrato> listarContratos() {
        return contratos.listar();
    }

    public List<Modelos.Contrato> listarContratos(String nome, String cpf, String imovel,
            String validade, String situacao) {
        return contratos.listar(nome, cpf, imovel, validade, situacao);
    }

    public List<ContratoOpcao> contratosOpcoes() {
        return contratos.opcoes().stream()
                .map(opcao -> new ContratoOpcao(opcao.id(), opcao.rotulo()))
                .toList();
    }

    public ContratoDetalhe buscarContrato(String id) {
        ContratoRepositorio.Detalhe d = contratos.buscar(id);
        return d == null ? null : contrato(d);
    }

    public ContratoDetalhe contratoVigentePorImovel(String imovelId) {
        ContratoRepositorio.Detalhe d = contratos.buscarVigentePorImovel(imovelId);
        return d == null ? null : contrato(d);
    }

    private static ContratoDetalhe contrato(ContratoRepositorio.Detalhe d) {
        return new ContratoDetalhe(d.id(), d.inquilino(), d.documento(), d.email(), d.endereco(),
                d.situacao(), d.aluguel(), d.inicio(), d.fim(), d.diaVencimento(),
                d.acaoJudicial(), d.acaoJudicialDescricao(), d.fiadorNome(), d.fiadorDocumento(),
                d.fiadorEmail(), d.fiadorTelefone(), d.caucaoDepositos(), d.caucaoSaldo(),
                d.imovelId());
    }

    public List<Modelos.Inquilino> listarInquilinos(String nome, String cpf, String email) {
        return inquilinos.listar(nome, cpf, email);
    }

    public InquilinoDadosEdicao detalheInquilino(String inquilinoId) {
        InquilinoRepositorio.DadosEdicao d = inquilinos.detalheEdicao(inquilinoId);
        return d == null ? null : new InquilinoDadosEdicao(d.imovelId(), d.aluguel(),
                d.diaVencimento(), d.nomeFiador(), d.documentoFiador(), d.emailFiador(),
                d.caucao());
    }

    public String contratoVigenteId(String inquilinoId) {
        return inquilinos.contratoVigenteId(inquilinoId);
    }

    public List<DocumentoLista> listarDocumentos(String nomeInquilino, String tipo,
            String tipoFinanceiro, int limite) {
        return documentos.listar(nomeInquilino, tipo, tipoFinanceiro, limite).stream()
                .map(d -> new DocumentoLista(d.id(), d.nomeArquivo(), d.nomeInquilino(), d.tipo(),
                        d.pendente(), d.meta(), d.financeiro(), d.data()))
                .toList();
    }

    public List<DocumentoOpcao> documentosOpcoes() {
        return documentos.opcoes().stream()
                .map(opcao -> new DocumentoOpcao(opcao.tipo(), opcao.id(), opcao.rotulo()))
                .toList();
    }

    public List<DocumentoVinculo> documentosPorVinculo(String tipo, String vinculoId) {
        return documentos.listarPorVinculo(tipo, vinculoId).stream()
                .map(d -> new DocumentoVinculo(d.id(), d.nomeArquivo(), d.tipo(), d.pendente()))
                .toList();
    }

    public List<ReformaImovel> reformasImoveis() {
        return reformas.listarImoveis().stream()
                .map(i -> new ReformaImovel(i.id(), i.endereco(), i.proprietario()))
                .toList();
    }

    public List<Modelos.Reforma> listarReformas(String imovelId) {
        return reformas.listar(imovelId);
    }

    @Transactional
    public void registrarReforma(String imovelId, LocalDate data, String descricao, BigDecimal valor,
            String formaPagamento, String nomeArquivo, String mime, byte[] comprovante) {
        reformas.salvarComComprovante(imovelId, data, descricao, valor, formaPagamento,
                nomeArquivo, mime, comprovante);
    }
}
