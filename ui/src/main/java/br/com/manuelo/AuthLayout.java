package br.com.manuelo;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.RouterLink;

public class AuthLayout extends Div {

    protected final Div form = new Div();
    private final Div brand;

    public AuthLayout() {
        addClassName("auth-shell");

        brand = new Div();
        brand.addClassName("auth-brand");
        Div marca = new Div();
        marca.addClassName("auth-brand-mark");
        marca.add(UiKit.logo("300px"));
        H1 nome = new H1("Fundação Manoel Cruz");
        nome.addClassName("auth-brand-title");
        brand.add(marca, nome);

        Div painel = new Div();
        painel.addClassName("auth-panel");
        form.addClassName("auth-form");
        painel.add(form);

        add(brand, painel);
    }

    protected void titulo(String texto, String subtitulo) {
        H1 titulo = new H1(texto);
        titulo.addClassName("auth-title");
        form.add(titulo);
        if (subtitulo != null && !subtitulo.isBlank()) {
            Paragraph sub = new Paragraph(subtitulo);
            sub.addClassName("auth-subtitle");
            form.add(sub);
        }
    }

    protected Span rotulo(String texto) {
        Span span = new Span(texto);
        span.addClassName("auth-label");
        return span;
    }

    protected RouterLink link(String texto, Class<? extends Component> destino) {
        RouterLink link = new RouterLink(texto, destino);
        link.addClassName("auth-link");
        return link;
    }

    protected Div conteudoCentral(Component... children) {
        Div div = new Div(children);
        div.addClassName("auth-center");
        return div;
    }

    protected Div checkCircle() {
        Div circle = new Div();
        circle.addClassName("auth-check");
        var icon = com.vaadin.flow.component.icon.VaadinIcon.CHECK.create();
        icon.addClassName("auth-check-icon");
        circle.add(icon);
        return circle;
    }
}
