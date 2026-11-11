package br.com.manuelo;

import java.util.List;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "contratos/detalhe", layout = MainLayout.class)
@PageTitle("Detalhes do contrato")
public class ContratoDetalheView extends Div implements BeforeEnterObserver {

    private final ContratoDialog novoContrato = new ContratoDialog(dados -> {
    });
    private DemoData.Contrato contrato = DemoData.contratos().get(0);

    public ContratoDetalheView() {
        addClassName("page");
        renderizar();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String id = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault("id", List.of("1")).stream().findFirst().orElse("1");
        contrato = DemoData.contratos().stream()
                .filter(c -> c.id().equals(id))
                .findFirst()
                .orElse(DemoData.contratos().get(0));
        renderizar();
    }

    private void renderizar() {
        removeAll();

        Button novo = UiKit.secondary("Novo contrato", e -> novoContrato.abrir());
        Button pagar = UiKit.primary("Pagar o aluguel", e -> new PagarAluguelDialog(contrato).open());
        add(UiKit.header("Contratos", null, novo, pagar));

        add(cardInquilino());
        add(cardContrato());
    }

    private Div cardInquilino() {
        TextField nome = somenteLeitura("Nome", contrato.inquilino());
        nome.addClassName("full");
        TextField endereco = somenteLeitura("Endereço", contrato.endereco());
        TextField cpf = somenteLeitura("CPF", contrato.cpf());
        TextField email = somenteLeitura("Email", contrato.email());
        TextField acaoJudicial = somenteLeitura("Tem ação judicial", "");
        acaoJudicial.setPlaceholder("Se sim, qual?");
        TextField caucaoInicial = somenteLeitura("Caução Inicial", "R$ 800");
        TextField caucaoAtual = somenteLeitura("Caução atual", "R$ 400");

        Div grid = UiKit.formGrid(nome, endereco, cpf, email, acaoJudicial, caucaoInicial, caucaoAtual);

        Div tituloFiador = new Div();
        tituloFiador.addClassName("section-title");
        tituloFiador.add(new Span("Fiador"));

        TextField fiadorNome = somenteLeitura("Nome", "Leocardi Daniel Oliveira de Oliveira");
        fiadorNome.addClassName("full");
        TextField fiadorEmail = somenteLeitura("Email", "Leo@wmail.com");
        fiadorEmail.addClassName("full");
        TextField fiadorNumero = somenteLeitura("Numero", "090123-5467");
        TextField fiadorCpf = somenteLeitura("CPF", "876.xxx.59-xy");

        Div gridFiador = UiKit.formGrid(fiadorNome, fiadorEmail, fiadorNumero, fiadorCpf);
        gridFiador.addClassName("fiador-grid");

        return UiKit.card(UiKit.cardHeader("Informações do Inquilino", null), grid,
                tituloFiador, gridFiador);
    }

    private Div cardContrato() {
        Div card = UiKit.card();
        card.add(UiKit.cardHeader("Contrato referente", null));
        card.add(UiKit.upload("Aqui vai ficar o contrato do inquilino", "PDF, JPG ou PNG, até 10 MB"));
        return card;
    }

    private TextField somenteLeitura(String label, String valor) {
        TextField field = UiKit.text(label, "");
        field.setValue(valor == null ? "" : valor);
        field.setReadOnly(true);
        return field;
    }
}
