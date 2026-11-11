package br.com.manuelo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("aprovacao")
@PageTitle("Aprovação")
public class AprovacaoView extends HorizontalLayout {

    public AprovacaoView() {
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        // Lado esquerdo
        Image logo = new Image("/images/logo.jpeg", "Brasão");
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
        H1 titulo = new H1("Bem Vindo à Fundação Manoel Cruz!");
        titulo.getStyle().set("color", "white").set("text-align", "center");

        Icon check = VaadinIcon.CHECK.create();
        check.setSize("80px");
        check.getStyle().set("color", "#1a1f44");

        Span circulo = new Span(check);
        circulo.getStyle()
                .set("background", "#e0e0e0")
                .set("border-radius", "50%")
                .set("width", "160px")
                .set("height", "160px")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("margin-top", "30px")
                .set("margin-bottom", "30px");

        Button retornar = new Button("Retornar");
        retornar.setWidth("60%");
        retornar.getStyle().set("background", "#fff9e8").set("color", "#1a1f44");
        retornar.addClickListener(e -> retornar.getUI().ifPresent(ui -> ui.navigate("")));

        Span aviso = new Span("Espere até o administrador aprovar sua entrada para ter acesso completo ao sistema ManuElo!");
        aviso.getStyle().set("color", "white").set("text-align", "center").set("font-size", "0.8rem");

        VerticalLayout form = new VerticalLayout(titulo, circulo, retornar, aviso);
        form.setWidth("400px");
        form.setPadding(false);
        form.setAlignItems(FlexComponent.Alignment.CENTER);

        VerticalLayout direita = new VerticalLayout(form);
        direita.setWidth("50%");
        direita.setHeight("100%");
        direita.setAlignItems(FlexComponent.Alignment.CENTER);
        direita.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        direita.getStyle().set("background", "#171A34");

        add(esquerda, direita);
    }
}