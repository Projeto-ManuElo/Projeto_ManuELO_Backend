package br.com.manuelo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import br.com.manuelo.dados.AutenticacaoService;

@Route("recuperar-senha")
@PageTitle("Recuperar Senha")
public class RecuperarSenhaView extends AuthLayout {

    public RecuperarSenhaView(AutenticacaoService autenticacao) {
        titulo("Recuperação de Senha", null);

        Div espaco = new Div();
        espaco.addClassName("auth-spacer-rec");
        form.add(espaco);

        EmailField email = UiKit.email("Email", "Digite o email que está cadastrado no sistema");
        Button recuperar = UiKit.primary("Recuperar", e -> {
            if (email.getValue() == null || email.getValue().isBlank()) {
                email.setInvalid(true);
                UiKit.erro("Informe o email cadastrado.");
                return;
            }
            email.setInvalid(false);
            autenticacao.solicitarRecuperacao(email.getValue());
            UiKit.ir("email-enviado");
        });
        recuperar.addClassName("auth-submit");
        recuperar.addClassName("auth-submit-rec");

        form.add(email, recuperar);
    }
}
