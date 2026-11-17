package br.com.manuelo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import br.com.manuelo.dados.AutenticacaoService;

@Route("cadastro")
@PageTitle("Cadastro")
public class CadastroView extends AuthLayout {

    public CadastroView(AutenticacaoService autenticacao) {
        form.addClassName("auth-cadastro");
        titulo("Cadastre-se!", null);

        TextField nome = UiKit.text("Nome", "");
        EmailField email = UiKit.email("Email", "");
        PasswordField senha = UiKit.password("Senha", "");
        PasswordField confirmar = UiKit.password("Confirmar Senha", "");

        Button cadastrar = UiKit.primary("Cadastrar", e -> {
            boolean invalido = nome.getValue() == null || nome.getValue().isBlank()
                    || email.getValue() == null || email.getValue().isBlank()
                    || senha.getValue() == null || senha.getValue().length() < 8
                    || !senha.getValue().equals(confirmar.getValue());
            nome.setInvalid(nome.getValue() == null || nome.getValue().isBlank());
            email.setInvalid(email.getValue() == null || email.getValue().isBlank());
            senha.setInvalid(senha.getValue() == null || senha.getValue().length() < 8);
            confirmar.setInvalid(senha.getValue() == null
                    || !senha.getValue().equals(confirmar.getValue()));
            if (invalido) {
                UiKit.erro("Preencha todos os campos. A senha deve ter ao menos 8 caracteres "
                        + "e ser igual à confirmação.");
                return;
            }
            if (autenticacao.cadastrar(nome.getValue(), email.getValue(), senha.getValue())) {
                UiKit.ir("aprovacao");
            } else {
                UiKit.erro("Não foi possível cadastrar. Verifique se o email já está em uso.");
            }
        });
        cadastrar.addClassName("auth-submit");

        form.add(nome, email, senha, confirmar, cadastrar,
                link("Esqueci a senha", RecuperarSenhaView.class));
    }
}
