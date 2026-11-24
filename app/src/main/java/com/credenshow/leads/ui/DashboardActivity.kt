package com.credenshow.leads.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.credenshow.leads.R
import com.credenshow.leads.data.Lead
import com.credenshow.leads.data.Rede
import com.credenshow.leads.data.Sessao
import com.credenshow.leads.databinding.ActivityDashboardBinding
import com.credenshow.leads.databinding.ItemLeadBinding
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    private lateinit var b: ActivityDashboardBinding
    private lateinit var sessao: Sessao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessao = Sessao(this)
        if (!sessao.pronto) { irParaProximaEtapa(sessao); return }

        b = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.tvOla.text = "Olá, ${sessao.usuarioNome.substringBefore(' ')}"
        b.tvEstande.text = sessao.estandeNome
        b.tvEvento.text = sessao.eventoNome
        b.lista.layoutManager = LinearLayoutManager(this)
        b.swipe.setColorSchemeResources(R.color.cs_primary)
        b.swipe.setOnRefreshListener { carregar() }

        // Trocar só aparece se houver o que trocar (mais de um evento ou estande)
        b.btnTrocar.visivel(sessao.eventos.size > 1 || sessao.qtdEstandesUsuario > 1)
        b.btnTrocar.setOnClickListener {
            if (sessao.qtdEstandesUsuario > 1) sessao.limparEstande() else sessao.limparEvento()
            irParaProximaEtapa(sessao)
        }
        b.btnSair.setOnClickListener { confirmarSair(sessao) }
        b.btnLer.setOnClickListener { startActivity(Intent(this, ScannerActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        if (::b.isInitialized) carregar()
        Atualizacao.verificar(this)
    }

    private fun carregar() {
        b.swipe.isRefreshing = true
        lifecycleScope.launch {
            try {
                val r = Rede.chamar { Rede.api(sessao).dashboard(sessao.eventoId, sessao.estandeId) }
                b.tvAviso.visivel(false)
                b.tvLeadsTotal.text = r.leads_total.toString()
                b.tvLeadsHoje.text = "${r.leads_hoje} hoje"
                b.tvEstandes.text = r.estandes_evento.toString()
                b.tvMeus.text = if (r.leads_meus == 1) "1 lido por você" else "${r.leads_meus} lidos por você"
                b.tvVazio.visivel(r.ultimos.isEmpty())
                b.lista.adapter = LeadsAdapter(r.ultimos) { mostrarLead(it) }
            } catch (e: Exception) {
                if (tratarErroDeSessao(e, sessao)) return@launch
                b.tvAviso.visivel(true)
                b.tvAviso.text = mensagemDe(e) + "\nPuxe a tela para baixo para tentar de novo."
            } finally {
                b.swipe.isRefreshing = false
            }
        }
    }

    private fun mostrarLead(l: Lead) {
        val linhas = listOfNotNull(
            l.empresa.takeIf { it.isNotBlank() }?.let { "🏢 $it" },
            l.cargo.takeIf { it.isNotBlank() }?.let { "💼 $it" },
            l.email.takeIf { it.isNotBlank() }?.let { "✉️ $it" },
            l.telefone.takeIf { it.isNotBlank() }?.let { "📞 $it" },
            l.interesse.takeIf { it.isNotBlank() }?.let { "\nInteresse: $it" },
            l.observacao.takeIf { it.isNotBlank() }?.let { "Observação: $it" },
            "\nLido em ${formatarDataHora(l.lido_em)}" + (if (l.coletado_por.isNotBlank()) " por ${l.coletado_por}" else "")
        )
        AlertDialog.Builder(this)
            .setTitle(l.nome)
            .setMessage(linhas.joinToString("\n"))
            .setPositiveButton("Fechar", null)
            .show()
    }

    class LeadsAdapter(private val itens: List<Lead>, private val aoClicar: (Lead) -> Unit) :
        RecyclerView.Adapter<LeadsAdapter.VH>() {
        class VH(val b: ItemLeadBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemLeadBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = itens.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val l = itens[pos]
            h.b.tvInicial.text = l.nome.trim().take(1).uppercase()
            h.b.tvNome.text = l.nome
            h.b.tvDetalhe.text = listOf(l.empresa, l.cargo).filter { it.isNotBlank() }.joinToString(" · ")
                .ifEmpty { l.email }
            h.b.tvHora.text = formatarDataHora(l.lido_em)
            h.b.root.setOnClickListener { aoClicar(l) }
        }
    }
}
