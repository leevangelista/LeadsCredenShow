package com.credenshow.leads.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.credenshow.leads.data.ParticipanteResposta
import com.credenshow.leads.data.Rede
import com.credenshow.leads.data.Sessao
import com.credenshow.leads.databinding.ActivityLeadBinding
import com.google.gson.Gson
import kotlinx.coroutines.launch

/** Dados do visitante lido + interesse/observação. Salvar volta para o leitor. */
class LeadActivity : AppCompatActivity() {

    private lateinit var b: ActivityLeadBinding
    private lateinit var sessao: Sessao
    private var tokenQr = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLeadBinding.inflate(layoutInflater)
        setContentView(b.root)
        sessao = Sessao(this)

        tokenQr = intent.getStringExtra(EXTRA_TOKEN_QR).orEmpty()
        val dados = try {
            Gson().fromJson(intent.getStringExtra(EXTRA_DADOS), ParticipanteResposta::class.java)
        } catch (_: Exception) { null }
        val p = dados?.participante
        if (p == null || tokenQr.isEmpty()) { finish(); return }

        b.tvNome.text = p.nome
        b.tvEmpresaCargo.text = listOf(p.empresa, p.cargo).filter { it.isNotBlank() }.joinToString(" · ")
        b.tvEmpresaCargo.visivel(b.tvEmpresaCargo.text.isNotEmpty())
        b.tvIngresso.text = p.ingresso
        b.tvIngresso.visivel(p.ingresso.isNotBlank())
        b.tvEmail.text = p.email.ifBlank { "E-mail não informado" }
        b.tvTelefone.text = p.telefone.ifBlank { "Telefone não informado" }

        dados.lead?.takeIf { dados.ja_coletado }?.let { l ->
            b.tvJaColetado.visivel(true)
            b.tvJaColetado.text = "Este visitante já é lead do estande (lido em ${formatarDataHora(l.lido_em)}" +
                (if (l.coletado_por.isNotBlank()) " por ${l.coletado_por}" else "") +
                "). Você pode atualizar as anotações."
            b.etInteresse.setText(l.interesse)
            b.etObservacao.setText(l.observacao)
            b.btnSalvar.text = "Atualizar lead"
        }

        b.btnVoltar.setOnClickListener { finish() }
        b.btnCancelar.setOnClickListener { finish() }
        b.btnSalvar.setOnClickListener { salvar() }
    }

    private fun salvar() {
        b.btnSalvar.isEnabled = false
        b.btnCancelar.isEnabled = false
        lifecycleScope.launch {
            try {
                val r = Rede.chamar {
                    Rede.api(sessao).salvarLead(
                        mapOf(
                            "evento_id" to sessao.eventoId,
                            "estande_id" to sessao.estandeId,
                            "token_qr" to tokenQr,
                            "interesse" to b.etInteresse.text?.toString()?.trim().orEmpty(),
                            "observacao" to b.etObservacao.text?.toString()?.trim().orEmpty()
                        )
                    )
                }
                Toast.makeText(this@LeadActivity, if (r.novo) "✅ Lead salvo!" else "✅ Lead atualizado!", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                b.btnSalvar.isEnabled = true
                b.btnCancelar.isEnabled = true
                if (tratarErroDeSessao(e, sessao)) return@launch
                alerta("Não foi possível salvar", mensagemDe(e))
            }
        }
    }

    companion object {
        const val EXTRA_TOKEN_QR = "token_qr"
        const val EXTRA_DADOS = "dados"
    }
}
