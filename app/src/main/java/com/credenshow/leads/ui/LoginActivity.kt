package com.credenshow.leads.ui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.credenshow.leads.BuildConfig
import com.credenshow.leads.data.Rede
import com.credenshow.leads.data.Sessao
import com.credenshow.leads.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var b: ActivityLoginBinding
    private lateinit var sessao: Sessao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessao = Sessao(this)

        // Já logado: segue direto (o servidor confere o token na próxima chamada)
        if (sessao.logado) {
            irParaProximaEtapa(sessao)
            return
        }

        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.etEmail.setText(sessao.ultimoEmail)
        if (sessao.ultimoEmail.isNotEmpty()) b.etSenha.requestFocus()
        b.tvVersao.text = "© CredenShow · versão ${BuildConfig.VERSION_NAME}"

        b.btnEntrar.setOnClickListener { entrar() }
        b.etSenha.setOnEditorActionListener { _, acao, _ ->
            if (acao == EditorInfo.IME_ACTION_DONE) { entrar(); true } else false
        }
    }

    override fun onResume() {
        super.onResume()
        Atualizacao.verificar(this)
    }

    private fun entrar() {
        val email = b.etEmail.text?.toString()?.trim().orEmpty()
        val senha = b.etSenha.text?.toString().orEmpty()

        b.tilEmail.error = null
        b.tilSenha.error = null
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { b.tilEmail.error = "Informe um e-mail válido"; return }
        if (senha.isEmpty()) { b.tilSenha.error = "Informe a senha"; return }

        carregando(true)
        lifecycleScope.launch {
            try {
                val r = Rede.chamar { Rede.api(sessao).login(mapOf("email" to email, "senha" to senha)) }
                sessao.ultimoEmail = email
                sessao.token = r.token
                sessao.usuarioNome = r.usuario?.nome.orEmpty()
                sessao.eventos = r.eventos
                sessao.limparEvento()

                if (r.primeiro_acesso) {
                    startActivity(Intent(this@LoginActivity, TrocarSenhaActivity::class.java)
                        .putExtra(TrocarSenhaActivity.EXTRA_SENHA_ATUAL, senha))
                    finish()
                } else {
                    irParaProximaEtapa(sessao)
                }
            } catch (e: Exception) {
                carregando(false)
                alerta("Não foi possível entrar", mensagemDe(e))
            }
        }
    }

    private fun carregando(sim: Boolean) {
        b.progress.visivel(sim)
        b.btnEntrar.isEnabled = !sim
        b.etEmail.isEnabled = !sim
        b.etSenha.isEnabled = !sim
    }
}
