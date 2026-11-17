package br.com.manuelo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("email-enviado")
@PageTitle("Email enviado")
public class EmailEnviadoView extends AuthLayout {

    public EmailEnviadoView() {
        form.addClassName("auth-form-center");
        titulo("Email de Redefinição de Senha Enviado!", null);
        form.add(checkCircle());

        Button retornar = UiKit.primary("Retornar", e -> UiKit.ir(""));
        retornar.addClassName("auth-submit");
        form.add(retornar);

        Span aviso = new Span("Veja seu email e redefina sua senha a partir do email recebido");
        aviso.addClassName("auth-note");
        aviso.addClassName("auth-note-sm");
        form.add(aviso);
    }
}
