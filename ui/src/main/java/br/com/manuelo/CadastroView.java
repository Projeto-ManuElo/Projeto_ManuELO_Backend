package br.com.manuelo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import com.Projeto.manuelo.seguranca.CadastroUsuarioService;

@Route("cadastro")
@PageTitle("Cadastro")
public class CadastroView extends AuthLayout {

    public CadastroView(CadastroUsuarioService cadastroUsuarios) {
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
            CadastroUsuarioService.Resultado resultado = cadastroUsuarios.cadastrarComResultado(
                    nome.getValue(), email.getValue(), senha.getValue());
            switch (resultado) {
                case OK -> UiKit.ir("aprovacao");
                case EMAIL_EM_USO -> UiKit.erro(
                        "Este email já está cadastrado. Use outro email ou recupere a senha.");
                case DADOS_INVALIDOS -> UiKit.erro(
                        "Dados inválidos. Verifique o nome, o email e a senha.");
            }
        });
        cadastrar.addClassName("auth-submit");

        form.add(nome, email, senha, confirmar, cadastrar,
                link("Esqueci a senha", RecuperarSenhaView.class));
    }
}
