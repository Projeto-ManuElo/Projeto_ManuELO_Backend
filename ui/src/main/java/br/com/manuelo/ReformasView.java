package br.com.manuelo;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "imoveis/reformas", layout = MainLayout.class)
@PageTitle("Histórico de reformas")
public class ReformasView extends Div {

    private static final DateTimeFormatter DATA_ANO = DateTimeFormatter.ofPattern("dd MMM yyyy", UiKit.PT_BR);

    private final List<DemoData.Reforma> reformas = DemoData.reformas();
    private final Div tabela = new Div();
    private final ReformaDialog dialog = new ReformaDialog(this::adicionar);

    public ReformasView() {
        addClassName("page");

        Button exportar = UiKit.secondary("Exportar", e -> UiKit.aviso("Exportação gerada com sucesso."));
        Button novo = UiKit.primaryIcon("Novo Registro", VaadinIcon.PLUS, e -> dialog.abrir());
        add(UiKit.header("Imóveis", null, exportar, novo));

        tabela.addClassName("table");
        Div card = UiKit.card(
                UiKit.cardHeader("Histórico de reformas",
                        "Proprietário atual: Leocardi Daniel Oliveira de Oliveira; Apto 302, ED. Aurora",
                        UiKit.secondary("Ver tudo", e -> UiKit.aviso("Histórico completo em preparação."))),
                tabela);
        add(card);
        renderizar();
    }

    private void renderizar() {
        tabela.removeAll();
        tabela.add(UiKit.tableHead("cols-reformas", "Data", "Descrição", "Proprietário do período", "Valor"));
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
        reformas.add(0, reforma);
        renderizar();
        UiKit.aviso("Registro de reforma adicionado.");
    }
}
