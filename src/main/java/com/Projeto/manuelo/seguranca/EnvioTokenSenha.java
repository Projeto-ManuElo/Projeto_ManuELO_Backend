package com.Projeto.manuelo.seguranca;

/**
 * A-13 (DEC-I05): entrega do token cru de redefinição de senha. A implementação real
 * envia por SMTP quando configurado, loga o link apenas em desenvolvimento e é neutra
 * nos demais casos.
 */
public interface EnvioTokenSenha {

    void enviar(String email, String token);
}
