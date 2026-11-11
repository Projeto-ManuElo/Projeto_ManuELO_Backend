package br.com.manuelo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;

@Route("aprovacao")
@RouteAlias("aguardando-aprovacao")
@PageTitle("Aguardando aprovação")
public class AprovacaoView extends AuthLayout {

    public AprovacaoView() {
        form.addClassName("auth-form-center");
        titulo("Bem Vindo à Fundação Manoel Cruz!", null);
        form.add(checkCircle());

        Button retornar = UiKit.primary("Retornar", e -> UiKit.ir(""));
        retornar.addClassName("auth-submit");
        form.add(retornar);

        Span aviso = new Span("Espere até o administrador aprovar sua entrada para ter acesso completo ao sistema ManuElo!");
        aviso.addClassName("auth-note");
        form.add(aviso);
    }
}
