package br.com.manuelo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("redefinir-senha")
@PageTitle("Redefinir Senha")
public class RedefinirSenhaView extends HorizontalLayout {

    public RedefinirSenhaView() {
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        // Lado esquerdo
        Image logo = new Image("images/logo.png", "Brasão");
        logo.setWidth("200px");
        Span nome = new Span("Fundação Manoel Cruz");
        nome.getStyle().set("font-size", "2rem").set("font-family", "serif");

        VerticalLayout esquerda = new VerticalLayout(logo, nome);
        esquerda.setWidth("50%");
        esquerda.setHeight("100%");
        esquerda.setAlignItems(FlexComponent.Alignment.CENTER);
        esquerda.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        esquerda.getStyle().set("background", "#fff9e8");

        // Lado direito
        H1 titulo = new H1("Redefina sua Senha!");
        titulo.getStyle().set("color", "white");

        PasswordField novaSenha = new PasswordField();
        PasswordField confirmar = new PasswordField();
        novaSenha.setWidthFull();
        confirmar.setWidthFull();

        Button redefinir = new Button("Redefinir");
        redefinir.setWidth("60%");
        redefinir.getStyle().set("background", "#fff9e8").set("color", "#1a1f44");
        redefinir.addClickListener(e -> redefinir.getUI().ifPresent(ui -> ui.navigate("")));

        VerticalLayout form = new VerticalLayout(
                titulo,
                rotulo("Novo Senha"), novaSenha,
                rotulo("Confirmar Nova Senha"), confirmar,
                redefinir);
        form.setWidth("400px");
        form.setPadding(false);
        form.setAlignSelf(FlexComponent.Alignment.CENTER, redefinir);

        VerticalLayout direita = new VerticalLayout(form);
        direita.setWidth("50%");
        direita.setHeight("100%");
        direita.setAlignItems(FlexComponent.Alignment.CENTER);
        direita.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        direita.getStyle().set("background", "#171A34");

        add(esquerda, direita);
    }

    private Span rotulo(String texto) {
        Span s = new Span(texto);
        s.getStyle().set("color", "white");
        return s;
    }
}