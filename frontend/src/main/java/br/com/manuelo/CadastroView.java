package br.com.manuelo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("cadastro")
@PageTitle("Cadastro")
public class CadastroView extends HorizontalLayout {

    public CadastroView() {
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        // Lado esquerdo
        Image logo = new Image("images/logo.jpeg", "Brasão");
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
        H1 titulo = new H1("Cadastre-se!");
        titulo.getStyle().set("color", "white");

        TextField campoNome = new TextField();
        EmailField email = new EmailField();
        PasswordField senha = new PasswordField();
        PasswordField confirmar = new PasswordField();
        campoNome.setWidthFull();
        email.setWidthFull();
        senha.setWidthFull();
        confirmar.setWidthFull();

        Button cadastrar = new Button("Cadastrar");
        cadastrar.setWidth("60%");
        cadastrar.getStyle().set("background", "#fff9e8").set("color", "#1a1f44");
        cadastrar.addClickListener(e -> cadastrar.getUI().ifPresent(ui -> ui.navigate("aprovacao")));

        Anchor esqueci = new Anchor("recuperar-senha", "Esqueci a senha");
        esqueci.getStyle().set("color", "white");

        VerticalLayout form = new VerticalLayout(
                titulo,
                rotulo("Nome"), campoNome,
                rotulo("Email"), email,
                rotulo("Senha"), senha,
                rotulo("Confirmar Senha"), confirmar,
                cadastrar, esqueci);
        form.setWidth("400px");
        form.setPadding(false);
        form.setAlignSelf(FlexComponent.Alignment.CENTER, cadastrar);

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