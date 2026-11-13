package br.com.manuelo;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import br.com.manuelo.dados.ReformaRepositorio;

@Route(value = "imoveis/reformas", layout = MainLayout.class)
@PageTitle("Histórico de reformas")
public class ReformasView extends Div {

    private static final DateTimeFormatter DATA_ANO = DateTimeFormatter.ofPattern("dd MMM yyyy", UiKit.PT_BR);

    private final ReformaRepositorio repositorio;
    private final ComboBox<ReformaRepositorio.ImovelInfo> seletor = new ComboBox<>("Imóvel");
    private final Span subtitulo = new Span();
    private ReformaRepositorio.ImovelInfo imovel;
    private List<DemoData.Reforma> reformas;
    private final Div tabela = new Div();
    private final ReformaDialog dialog;

    public ReformasView(ReformaRepositorio repositorio) {
        this.repositorio = repositorio;
        this.dialog = new ReformaDialog(this::adicionar);
        addClassName("page");

        List<ReformaRepositorio.ImovelInfo> imoveis = repositorio.listarImoveis();
        this.imovel = imoveis.isEmpty() ? null : imoveis.get(0);
        this.reformas = imovel == null ? List.of() : repositorio.listar(imovel.id());

        seletor.setItems(imoveis);
        seletor.setItemLabelGenerator(ReformaRepositorio.ImovelInfo::endereco);
        seletor.setPlaceholder("Selecione o imóvel");
        seletor.setWidth("320px");
        seletor.addValueChangeListener(event -> {
            if (event.getValue() != null) {
                selecionar(event.getValue());
            }
        });

        Button novo = UiKit.primaryIcon("Novo Registro", VaadinIcon.PLUS, e -> abrirNovo());
        add(UiKit.header("Reformas", null, novo));

        tabela.addClassName("table");
        Div card = UiKit.card(
                UiKit.cardHeaderComSubtitulo("Histórico de reformas", subtitulo, seletor),
                tabela);
        add(card);

        if (imovel != null) {
            seletor.setValue(imovel);
        }
        atualizarSubtitulo();
        renderizar();
    }

    private void selecionar(ReformaRepositorio.ImovelInfo selecionado) {
        this.imovel = selecionado;
        this.reformas = repositorio.listar(selecionado.id());
        atualizarSubtitulo();
        renderizar();
    }

    private void atualizarSubtitulo() {
        subtitulo.setText(textoSubtitulo());
    }

    private String textoSubtitulo() {
        if (imovel == null) {
            return "Selecione um imóvel para ver o histórico.";
        }
        return "Proprietário atual: "
                + (imovel.proprietario() == null ? "sem contrato vigente" : imovel.proprietario())
                + "; " + imovel.endereco();
    }

    private void abrirNovo() {
        if (imovel == null) {
            UiKit.erro("Selecione um imóvel antes de registrar reformas.");
            return;
        }
        dialog.abrir(imovel.proprietario() == null ? "" : imovel.proprietario());
    }

    private void renderizar() {
        tabela.removeAll();
        tabela.add(UiKit.tableHead("cols-reformas", "Data", "Descrição", "Proprietário do período", "Valor"));
        if (reformas.isEmpty()) {
            tabela.add(UiKit.emptyState("Nenhuma reforma registrada",
                    "Use o botão Novo Registro para adicionar."));
            return;
        }
        for (DemoData.Reforma reforma : reformas) {
            String data = reforma.data().getYear() >= 2025
                    ? UiKit.diaMes(reforma.data())
                    : DATA_ANO.format(reforma.data()).replace(".", "");
            Span valor = UiKit.money(reforma.valor().negate(), false);
            valor.addClassName("amount");
            Span proprietario = new Span(reforma.proprietario());
            proprietario.addClassName("owner-link");
            tabela.add(UiKit.tableRow("cols-reformas",
                    new Span(data),
                    new Span(reforma.descricao()),
                    proprietario,
                    valor));
        }
    }

    private void adicionar(DemoData.Reforma reforma) {
        if (imovel == null) {
            UiKit.erro("Selecione um imóvel antes de registrar reformas.");
            return;
        }
        try {
            repositorio.salvar(imovel.id(), reforma.data(), reforma.descricao(), reforma.valor());
            reformas = repositorio.listar(imovel.id());
            renderizar();
            UiKit.aviso("Registro de reforma adicionado.");
        } catch (RuntimeException ex) {
            UiKit.erro(ex.getMessage());
        }
    }
}
