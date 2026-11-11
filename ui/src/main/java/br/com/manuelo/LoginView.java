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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("")
@PageTitle("Login")
public class LoginView extends HorizontalLayout {

    public LoginView() {
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        //Lado esquerdo
        Image logo = new Image("/images/logo.jpeg", "Brasão");
        logo.setWidth("200px");
        Span nome = new Span("Fundação Manoel Cruz");
        nome.getStyle().set("font-size", "2rem").set("font-family", "serif");

        VerticalLayout esquerda = new VerticalLayout(logo, nome);
        esquerda.setWidth("50%");
        esquerda.setHeight("100%");
        esquerda.setAlignItems(FlexComponent.Alignment.CENTER);
        esquerda.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        esquerda.getStyle().set("background", "#f2f2f2");

        //Lado direito
        H1 titulo = new H1("Bem vindo!");
        titulo.getStyle().set("color", "white");

        EmailField email = new EmailField();
        email.setWidthFull();
        PasswordField senha = new PasswordField();
        senha.setWidthFull();

        Button login = new Button("Login");
        login.setWidth("60%");
        login.addClickListener(e -> login.getUI().ifPresent(ui -> ui.navigate("home")));

        Anchor esqueci = new Anchor("recuperar-senha", "Esqueci a senha");
        esqueci.getStyle().set("color", "white");
        Anchor cadastro = new Anchor("cadastro", "Cadastre-se");
        cadastro.getStyle().set("color", "white");

        VerticalLayout form = new VerticalLayout(
                titulo, rotulo("Email"), email, rotulo("Senha"), senha, login, esqueci, cadastro);
        form.setWidth("400px");
        form.setPadding(false);
        form.setAlignSelf(FlexComponent.Alignment.CENTER, login);

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