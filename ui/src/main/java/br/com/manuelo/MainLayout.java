package br.com.manuelo;

import java.util.LinkedHashMap;
import java.util.Map;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.router.RouterLink;

public class MainLayout extends Div implements RouterLayout, AfterNavigationObserver {

    private final Map<String, RouterLink> links = new LinkedHashMap<>();
    private final Div conteudo = new Div();

    public MainLayout() {
        addClassName("app-shell");
        Div sidebar = criarSidebar();
        conteudo.addClassName("app-content");
        add(sidebar, conteudo);
    }

    private Div criarSidebar() {
        Div sidebar = new Div();
        sidebar.addClassName("sidebar");

        Div marca = new Div();
        marca.addClassName("sidebar-brand");
        marca.add(UiKit.logo("36px"));
        Div textos = new Div();
        textos.addClassName("sidebar-brand-copy");
        Span nome = new Span("ManuElo");
        nome.addClassName("sidebar-brand-name");
        Span sub = new Span("Fundação Manoel Cruz");
        sub.addClassName("sidebar-brand-sub");
        textos.add(nome, sub);
        marca.add(textos);

        Div nav = new Div();
        nav.addClassName("sidebar-nav");
        nav.add(item(VaadinIcon.HOME, "Visão geral", HomeView.class, "home"));
        nav.add(secao("Financeiro"));
        nav.add(item(VaadinIcon.BUILDING, "Imóveis", ImoveisView.class, "imoveis"));
        nav.add(item(VaadinIcon.USERS, "Inquilinos", InquilinosView.class, "inquilinos"));
        nav.add(item(VaadinIcon.FILE_TEXT, "Contratos", ContratosView.class, "contratos"));
        nav.add(item(VaadinIcon.MONEY, "Movimentação Financeira", MovimentacaoFinanceiraView.class,
                "movimentacao-financeira"));
        nav.add(secao("Gestão documental"));
        nav.add(item(VaadinIcon.FOLDER, "Documentos", DocumentosView.class, "documentos"));
        nav.add(item(VaadinIcon.BAR_CHART, "Relatórios", RelatoriosView.class, "relatorios"));

        Div usuario = new Div();
        usuario.addClassName("sidebar-user");
        Div avatar = new Div(new Span("AF"));
        avatar.addClassName("sidebar-avatar");
        Div dados = new Div();
        dados.addClassName("sidebar-user-copy");
        Span nomeUsuario = new Span("Ana Ferreira");
        nomeUsuario.addClassName("sidebar-user-name");
        Span cargo = new Span("Administradora");
        cargo.addClassName("sidebar-user-role");
        dados.add(nomeUsuario, cargo);
        usuario.add(avatar, dados);

        sidebar.add(marca, nav, usuario);
        return sidebar;
    }

    private Span secao(String texto) {
        Span span = new Span(texto);
        span.addClassName("nav-section");
        return span;
    }

    private RouterLink item(VaadinIcon icone, String texto, Class<? extends Component> destino, String chave) {
        Icon icon = icone.create();
        icon.addClassName("nav-icon");
        Span label = new Span(texto);
        label.addClassName("nav-label");
        RouterLink link = new RouterLink();
        link.setRoute(destino);
        link.add(icon, label);
        link.addClassName("nav-item");
        links.put(chave, link);
        return link;
    }

    @Override
    public void showRouterLayoutContent(HasElement content) {
        conteudo.removeAll();
        if (content != null) {
            conteudo.getElement().appendChild(content.getElement());
        }
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        String path = event.getLocation().getPath();
        String ativo = path.isBlank() ? "home" : path.split("/")[0];
        links.forEach((chave, link) -> {
            link.removeClassName("active");
            if (chave.equals(ativo)) {
                link.addClassName("active");
            }
        });
    }
}
