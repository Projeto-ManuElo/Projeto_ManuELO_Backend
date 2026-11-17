package br.com.manuelo;

import java.time.LocalDate;
import java.util.List;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import com.Projeto.manuelo.servico.DocumentoService;
import com.Projeto.manuelo.servico.InquilinoCadastroService;

import br.com.manuelo.dados.ImovelRepositorio;
import br.com.manuelo.dados.InquilinoRepositorio;

@Route(value = "inquilinos", layout = MainLayout.class)
@PageTitle("Inquilinos")
public class InquilinosView extends Div {

    private static final int PAGINA = 30;

    private final InquilinoRepositorio repositorio;
    private final InquilinoCadastroService cadastro;
    private final DocumentoService documentos;
    private final TextField nome = UiKit.text("Nome", "Ex: João Miguel");
    private final TextField cpf = UiKit.text("CPF", "Ex: xxx.yyy.zzz.xz");
    private final TextField email = UiKit.text("Email", "Ex: joao@wmail.com");
    private final Div tabela = new Div();
    private final Div paginacao = new Div();
    private final InquilinoDialog dialog;
    private int limite = PAGINA;

    public InquilinosView(InquilinoRepositorio repositorio, InquilinoCadastroService cadastro,
            ImovelRepositorio imoveis, DocumentoService documentos) {
        this.repositorio = repositorio;
        this.cadastro = cadastro;
        this.documentos = documentos;
        addClassName("page");
        dialog = new InquilinoDialog(imoveis.opcoes(), this::salvar);

        Button novo = UiKit.primaryIcon("Novo Inquilino", VaadinIcon.PLUS, e -> dialog.abrirNovo());
        add(UiKit.header("Inquilinos", null, novo));

        add(criarFiltros());
        add(criarTabelaCard());
        renderizar();
    }

    private Div criarFiltros() {
        Div acoes = new Div(
                UiKit.secondary("Cancelar", e -> {
                    limpar();
                    limite = PAGINA;
                    renderizar();
                }),
                UiKit.primary("Filtrar", e -> {
                    limite = PAGINA;
                    renderizar();
                }));
        acoes.addClassName("form-actions");

        nome.addClassName("full");
        Div grid = UiKit.formGrid(nome, cpf, email);
        return UiKit.card(UiKit.cardHeader("Filtros", null), grid, acoes);
    }

    private Div criarTabelaCard() {
        tabela.addClassName("table");
        paginacao.addClassName("form-actions");
        return UiKit.card(UiKit.cardHeader("Inquilinos Cadastrados", null), tabela, paginacao);
    }

    private void limpar() {
        nome.clear();
        cpf.clear();
        email.clear();
    }

    private void renderizar() {
        tabela.removeAll();
        paginacao.removeAll();
        tabela.add(UiKit.tableHead("cols-inquilinos", "Nome", "CPF", "Email"));

        List<Modelos.Inquilino> filtrados = repositorio.listar(nome.getValue(), cpf.getValue(),
                email.getValue());

        if (filtrados.isEmpty()) {
            tabela.add(UiKit.emptyState("Nenhum inquilino encontrado", "Ajuste os filtros e tente novamente."));
            return;
        }

        boolean temMais = filtrados.size() > limite;
        List<Modelos.Inquilino> exibidos = temMais ? filtrados.subList(0, limite) : filtrados;
        for (Modelos.Inquilino inquilino : exibidos) {
            Div linha = UiKit.tableRow("cols-inquilinos",
                    new Span(inquilino.nome()),
                    new Span(inquilino.cpf()),
                    UiKit.muted(inquilino.email()));
            linha.addClassName("clickable");
            linha.addClickListener(e -> dialog.abrirEdicao(inquilino,
                    repositorio.detalheEdicao(inquilino.id())));
            tabela.add(linha);
        }
        if (temMais) {
            paginacao.add(UiKit.secondary("Carregar mais", e -> {
                limite += PAGINA;
                renderizar();
            }));
        } else {
            paginacao.add(UiKit.muted("Exibindo todos os inquilinos encontrados."));
        }
    }

    private void salvar(InquilinoDialog.DadosInquilino dados) {
        try {
            String inquilinoId = cadastro.salvar(new InquilinoCadastroService.Dados(dados.id(), dados.nome(),
                    dados.cpf(), dados.email(), dados.telefone(), dados.nomeFiador(),
                    dados.documentoFiador(), dados.emailFiador(), dados.imovelId(),
                    dados.aluguel(), dados.diaVencimento(), dados.caucao(), null));
            salvarDocumentos(inquilinoId, dados);
            renderizar();
            UiKit.aviso("Inquilino salvo com sucesso.");
        } catch (RuntimeException ex) {
            UiKit.erro(ex.getMessage());
        }
    }

    private void salvarDocumentos(String inquilinoId, InquilinoDialog.DadosInquilino dados) {
        UiKit.ArquivoSelecionado contrato = dados.contratoArquivo();
        if (contrato != null && contrato.conteudo() != null && contrato.conteudo().length > 0) {
            String contratoId = repositorio.contratoVigenteId(inquilinoId);
            if (contratoId != null) {
                documentos.salvar("Contrato - " + dados.nome(), "Contrato", LocalDate.now(),
                        "CONTRATO", contratoId, contrato.nome(), contrato.mime(),
                        contrato.conteudo());
            } else {
                documentos.salvar("Contrato - " + dados.nome(), null, null, "INQUILINO",
                        inquilinoId, contrato.nome(), contrato.mime(), contrato.conteudo());
            }
        }
        UiKit.ArquivoSelecionado recibo = dados.reciboArquivo();
        if (recibo != null && recibo.conteudo() != null && recibo.conteudo().length > 0) {
            documentos.salvar("Recibo de caução - " + dados.nome(), "Comprovante",
                    LocalDate.now(), "INQUILINO", inquilinoId, recibo.nome(), recibo.mime(),
                    recibo.conteudo());
        }
    }
}
