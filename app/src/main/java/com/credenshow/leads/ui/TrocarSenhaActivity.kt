package com.credenshow.leads.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.credenshow.leads.data.ErroApi
import com.credenshow.leads.data.Rede
import com.credenshow.leads.data.Sessao
import com.credenshow.leads.databinding.ActivityTrocarSenhaBinding
import kotlinx.coroutines.launch

/** Troca de senha obrigatória no primeiro acesso. */
class TrocarSenhaActivity : AppCompatActivity() {

    private lateinit var b: ActivityTrocarSenhaBinding
    private lateinit var sessao: Sessao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityTrocarSenhaBinding.inflate(layoutInflater)
        setContentView(b.root)
        sessao = Sessao(this)

        // Vindo do login, a senha atual já é conhecida: o usuário só digita a nova
        intent.getStringExtra(EXTRA_SENHA_ATUAL)?.let {
            b.etAtual.setText(it)
            b.tilAtual.visivel(false)
            b.etNova.requestFocus()
        }

        b.btnSalvar.setOnClickListener { salvar() }
        b.btnSair.setOnClickListener {
            sessao.sair()
            irParaProximaEtapa(sessao)
        }
    }

    private fun salvar() {
        val atual = b.etAtual.text?.toString().orEmpty()
        val nova = b.etNova.text?.toString().orEmpty()
        val confirmar = b.etConfirmar.text?.toString().orEmpty()

        b.tilAtual.error = null; b.tilNova.error = null; b.tilConfirmar.error = null
        if (atual.isEmpty()) { b.tilAtual.visivel(true); b.tilAtual.error = "Informe a senha atual"; return }
        if (nova.length < 6) { b.tilNova.error = "Mínimo de 6 caracteres"; return }
        if (nova == atual) { b.tilNova.error = "A nova senha precisa ser diferente da atual"; return }
        if (nova != confirmar) { b.tilConfirmar.error = "As senhas não conferem"; return }

        carregando(true)
        lifecycleScope.launch {
            try {
                val r = Rede.chamar {
                    Rede.api(sessao).trocarSenha(mapOf("senha_atual" to atual, "nova_senha" to nova))
                }
                sessao.token = r.token // a senha nova invalida o token antigo
                Toast.makeText(this@TrocarSenhaActivity, "Senha alterada!", Toast.LENGTH_SHORT).show()
                irParaProximaEtapa(sessao)
            } catch (e: Exception) {
                carregando(false)
                if (tratarErroDeSessao(e, sessao)) return@launch
                if ((e as? ErroApi)?.codigo == "senha_atual") {
                    b.tilAtual.visivel(true)
                    b.tilAtual.error = e.message
                } else {
                    alerta("Não foi possível trocar a senha", mensagemDe(e))
                }
            }
        }
    }

    private fun carregando(sim: Boolean) {
        b.progress.visivel(sim)
        b.btnSalvar.isEnabled = !sim
    }

    companion object {
        const val EXTRA_SENHA_ATUAL = "senha_atual"
    }
}
