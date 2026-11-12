package br.com.manuelo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import br.com.manuelo.dados.AutenticacaoService;

@Route("redefinir-senha")
@PageTitle("Redefinir Senha")
public class RedefinirSenhaView extends AuthLayout {

    public RedefinirSenhaView(AutenticacaoService autenticacao) {
        titulo("Redefina sua Senha!", null);

        Div espaco = new Div();
        espaco.addClassName("auth-spacer-red");
        form.add(espaco);

        PasswordField novaSenha = UiKit.password("Nova Senha", "");
        PasswordField confirmar = UiKit.password("Confirmar Nova Senha", "");
        novaSenha.addClassName("auth-mb");
        Button redefinir = UiKit.primary("Redefinir", e -> {
            boolean invalido = novaSenha.getValue() == null || novaSenha.getValue().length() < 8
                    || !novaSenha.getValue().equals(confirmar.getValue());
            novaSenha.setInvalid(novaSenha.getValue() == null || novaSenha.getValue().length() < 8);
            confirmar.setInvalid(novaSenha.getValue() == null
                    || !novaSenha.getValue().equals(confirmar.getValue()));
            if (invalido) {
                UiKit.erro("A senha deve ter ao menos 8 caracteres e ser igual à confirmação.");
                return;
            }
            if (autenticacao.redefinirSenha(novaSenha.getValue())) {
                UiKit.aviso("Senha redefinida com sucesso.");
                UiKit.ir("");
            } else {
                UiKit.erro("Solicite a recuperação de senha novamente.");
            }
        });
        redefinir.addClassName("auth-submit");
        redefinir.addClassName("auth-submit-red");

        form.add(novaSenha, confirmar, redefinir);
    }
}
