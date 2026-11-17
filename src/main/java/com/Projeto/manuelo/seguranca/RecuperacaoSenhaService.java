package com.Projeto.manuelo.seguranca;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Projeto.manuelo.dominio.TokenRedefinicaoSenha;
import com.Projeto.manuelo.dominio.Usuario;
import com.Projeto.manuelo.dominio.enums.StatusUsuario;
import com.Projeto.manuelo.dominio.repositorio.TokenRedefinicaoSenhaJpaRepository;
import com.Projeto.manuelo.dominio.repositorio.UsuarioJpaRepository;

/**
 * A-13 (DEC-I05): solicitação e redefinição de senha por token. O banco guarda apenas
 * o SHA-256 hexadecimal do token cru; o token tem 32 bytes aleatórios, validade de 1
 * hora e uso único. A solicitação responde de forma neutra para e-mail inexistente.
 */
@Service
@Transactional
public class RecuperacaoSenhaService {

    public enum Resultado {
        OK, INVALIDO
    }

    private static final Logger LOG = LoggerFactory.getLogger(RecuperacaoSenhaService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TAMANHO_TOKEN_BYTES = 32;
    private static final int TAMANHO_MINIMO_SENHA = 8;

    private final UsuarioJpaRepository usuarios;
    private final TokenRedefinicaoSenhaJpaRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final EnvioTokenSenha envio;

    public RecuperacaoSenhaService(UsuarioJpaRepository usuarios,
            TokenRedefinicaoSenhaJpaRepository tokens, PasswordEncoder passwordEncoder,
            EnvioTokenSenha envio) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.envio = envio;
    }

    public void solicitar(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String normalizado = email.trim();
        Usuario usuario = usuarios.findByEmailIgnoreCase(normalizado).orElse(null);
        if (usuario == null || usuario.getId() == null
                || usuario.getStatus() != StatusUsuario.ATIVO) {
            return;
        }

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        invalidarTokensAnteriores(usuario.getId(), agora);

        String tokenCru = gerarTokenCru();
        TokenRedefinicaoSenha token = new TokenRedefinicaoSenha();
        token.setUsuarioId(usuario.getId());
        token.setTokenHash(sha256Hex(tokenCru));
        token.setExpiraEm(agora.plusHours(1));
        tokens.save(token);

        try {
            envio.enviar(usuario.getEmail(), tokenCru);
        } catch (RuntimeException ex) {
            LOG.warn("Falha ao entregar token de redefinição de senha: {}",
                    ex.getClass().getSimpleName());
        }
    }

    public Resultado redefinir(String token, String novaSenha) {
        if (token == null || token.isBlank()
                || novaSenha == null || novaSenha.length() < TAMANHO_MINIMO_SENHA) {
            return Resultado.INVALIDO;
        }

        TokenRedefinicaoSenha registro = tokens.findByTokenHash(sha256Hex(token.trim()))
                .orElse(null);
        if (registro == null || registro.getUsadoEm() != null) {
            return Resultado.INVALIDO;
        }
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        if (registro.getExpiraEm() == null || !registro.getExpiraEm().isAfter(agora)) {
            return Resultado.INVALIDO;
        }

        Usuario usuario = usuarios.findById(registro.getUsuarioId()).orElse(null);
        if (usuario == null || usuario.getStatus() != StatusUsuario.ATIVO) {
            return Resultado.INVALIDO;
        }

        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        usuarios.save(usuario);

        registro.setUsadoEm(agora);
        tokens.save(registro);
        invalidarTokensAnteriores(usuario.getId(), agora);
        return Resultado.OK;
    }

    private void invalidarTokensAnteriores(UUID usuarioId, OffsetDateTime agora) {
        List<TokenRedefinicaoSenha> pendentes = tokens.findByUsuarioIdAndUsadoEmIsNull(usuarioId);
        for (TokenRedefinicaoSenha pendente : pendentes) {
            pendente.setUsadoEm(agora);
        }
        if (!pendentes.isEmpty()) {
            tokens.saveAll(pendentes);
        }
    }

    private static String gerarTokenCru() {
        byte[] bytes = new byte[TAMANHO_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String valor) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível", ex);
        }
    }
}
