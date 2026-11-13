package br.com.manuelo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import com.Projeto.manuelo.seguranca.RecuperacaoSenhaService;

@Route("redefinir-senha")
@PageTitle("Redefinir Senha")
public class RedefinirSenhaView extends AuthLayout implements BeforeEnterObserver {

    private final RecuperacaoSenhaService recuperacao;
    private final Button redefinir;
    private final TextField tokenManual;
    private String token;

    public RedefinirSenhaView(RecuperacaoSenhaService recuperacao) {
        this.recuperacao = recuperacao;
        titulo("Redefina sua Senha!", null);

        Div espaco = new Div();
        espaco.addClassName("auth-spacer-red");
        form.add(espaco);

        tokenManual = UiKit.text("Código do link", "Cole o código recebido");
        PasswordField novaSenha = UiKit.password("Nova Senha", "");
        PasswordField confirmar = UiKit.password("Confirmar Nova Senha", "");
        novaSenha.addClassName("auth-mb");
        redefinir = UiKit.primary("Redefinir", e -> redefinir(novaSenha, confirmar));
        redefinir.addClassName("auth-submit");
        redefinir.addClassName("auth-submit-red");

        form.add(tokenManual, novaSenha, confirmar, redefinir);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        token = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault("token", java.util.List.of())
                .stream()
                .findFirst()
                .orElse(null);
        if (token == null || token.isBlank()) {
            token = null;
            return;
        }
        tokenManual.setValue(token);
    }

    private void redefinir(PasswordField novaSenha, PasswordField confirmar) {
        String codigo = tokenManual.getValue();
        if (codigo == null || codigo.isBlank()) {
            codigo = token;
        }
        if (codigo == null || codigo.isBlank()) {
            tokenManual.setInvalid(true);
            UiKit.erro("Informe o código do link recebido por email ou abra o link completo.");
            return;
        }
        tokenManual.setInvalid(false);
        boolean invalido = novaSenha.getValue() == null || novaSenha.getValue().length() < 8
                || !novaSenha.getValue().equals(confirmar.getValue());
        novaSenha.setInvalid(novaSenha.getValue() == null || novaSenha.getValue().length() < 8);
        confirmar.setInvalid(novaSenha.getValue() == null
                || !novaSenha.getValue().equals(confirmar.getValue()));
        if (invalido) {
            UiKit.erro("A senha deve ter ao menos 8 caracteres e ser igual à confirmação.");
            return;
        }
        if (recuperacao.redefinir(codigo, novaSenha.getValue()) == RecuperacaoSenhaService.Resultado.OK) {
            UiKit.aviso("Senha redefinida com sucesso.");
            UiKit.ir("");
        } else {
            UiKit.erro("Código inválido ou expirado. Solicite novamente.");
        }
    }
}
