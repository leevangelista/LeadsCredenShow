package com.credenshow.leads.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.credenshow.leads.R
import com.credenshow.leads.data.Estande
import com.credenshow.leads.data.Evento
import com.credenshow.leads.data.Rede
import com.credenshow.leads.data.Sessao
import com.credenshow.leads.databinding.ActivitySelecionarBinding
import com.credenshow.leads.databinding.ItemOpcaoBinding
import kotlinx.coroutines.launch

/**
 * Etapa 1: escolher o evento (pula se só houver um).
 * Etapa 2: escolher o estande (pula se só houver um).
 */
class SelecionarActivity : AppCompatActivity() {

    private lateinit var b: ActivitySelecionarBinding
    private lateinit var sessao: Sessao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySelecionarBinding.inflate(layoutInflater)
        setContentView(b.root)
        sessao = Sessao(this)

        b.tvOla.text = "Olá, ${sessao.usuarioNome.substringBefore(' ')}"
        b.lista.layoutManager = LinearLayoutManager(this)
        b.btnSair.setOnClickListener { confirmarSair(sessao) }
        b.btnVoltar.setOnClickListener { voltarParaEventos() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (sessao.eventoId > 0 && sessao.eventos.size > 1) voltarParaEventos() else finish()
            }
        })

        if (sessao.eventoId > 0) carregarEstandes() else mostrarEventos()
    }

    private fun mostrarEventos() {
        val eventos = sessao.eventos
        if (eventos.size == 1) {
            escolherEvento(eventos[0])
            return
        }
        b.btnVoltar.visivel(false)
        b.tvTitulo.text = "Escolha o evento"
        b.tvSubtitulo.text = "Em qual evento você vai coletar leads?"
        b.boxErro.visivel(eventos.isEmpty())
        b.tvErro.text = "Nenhum evento ativo vinculado ao seu usuário."
        b.btnTentar.visivel(false)
        b.lista.adapter = OpcoesAdapter(eventos.map {
            Opcao(it.nome, periodo(it), R.drawable.ic_event) { escolherEvento(it) }
        })
    }

    private fun escolherEvento(e: Evento) {
        sessao.eventoId = e.id
        sessao.eventoNome = e.nome
        sessao.limparEstande()
        carregarEstandes()
    }

    private fun voltarParaEventos() {
        sessao.limparEvento()
        mostrarEventos()
    }

    private fun carregarEstandes() {
        b.btnVoltar.visivel(sessao.eventos.size > 1)
        b.tvTitulo.text = "Escolha o estande"
        b.tvSubtitulo.text = sessao.eventoNome
        b.lista.adapter = OpcoesAdapter(emptyList())
        b.boxErro.visivel(false)
        b.progress.visivel(true)

        lifecycleScope.launch {
            try {
                val estandes = Rede.chamar { Rede.api(sessao).estandes(sessao.eventoId) }.estandes
                b.progress.visivel(false)
                sessao.qtdEstandesUsuario = estandes.size
                if (estandes.size == 1) {
                    escolherEstande(estandes[0])
                    return@launch
                }
                b.lista.adapter = OpcoesAdapter(estandes.map {
                    Opcao(it.empresa, subtituloEstande(it), R.drawable.ic_groups) { escolherEstande(it) }
                })
            } catch (e: Exception) {
                b.progress.visivel(false)
                if (tratarErroDeSessao(e, sessao)) return@launch
                b.boxErro.visivel(true)
                b.tvErro.text = mensagemDe(e)
                b.btnTentar.visivel(true)
                b.btnTentar.setOnClickListener { carregarEstandes() }
            }
        }
    }

    private fun escolherEstande(e: Estande) {
        sessao.estandeId = e.id
        sessao.estandeNome = if (e.empresa != e.codigo) "${e.empresa} · ${e.codigo}" else e.empresa
        irParaProximaEtapa(sessao)
    }

    private fun subtituloEstande(e: Estande): String = listOfNotNull(
        "Estande ${e.codigo}".takeIf { e.codigo != e.empresa },
        e.pavilhao?.takeIf { it.isNotBlank() }?.let { "Pavilhão $it" }
    ).joinToString(" · ").ifEmpty { "Estande" }

    private fun periodo(e: Evento): String {
        fun br(d: String?) = d?.split("-")?.takeIf { it.size == 3 }?.let { "${it[2]}/${it[1]}/${it[0]}" }
        val ini = br(e.data_inicio)
        val fim = br(e.data_fim)
        return when {
            ini != null && fim != null && ini != fim -> "$ini a $fim"
            ini != null -> ini
            else -> ""
        }
    }

    data class Opcao(val titulo: String, val subtitulo: String, val icone: Int, val aoClicar: () -> Unit)

    class OpcoesAdapter(private val itens: List<Opcao>) : RecyclerView.Adapter<OpcoesAdapter.VH>() {
        class VH(val b: ItemOpcaoBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemOpcaoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = itens.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val o = itens[pos]
            h.b.tvTitulo.text = o.titulo
            h.b.tvSubtitulo.text = o.subtitulo
            h.b.tvSubtitulo.visivel(o.subtitulo.isNotEmpty())
            h.b.ivIcone.setImageResource(o.icone)
            h.b.root.setOnClickListener { o.aoClicar() }
        }
    }
}
