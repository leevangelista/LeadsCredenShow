package com.credenshow.leads.ui

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.credenshow.leads.data.ErroApi
import com.credenshow.leads.data.Sessao

/** Abre a próxima tela conforme o que falta escolher, limpando as telas anteriores. */
fun Activity.irParaProximaEtapa(sessao: Sessao) {
    val destino = when {
        !sessao.logado -> LoginActivity::class.java
        sessao.eventoId == 0 || sessao.estandeId == 0 -> SelecionarActivity::class.java
        else -> DashboardActivity::class.java
    }
    startActivity(Intent(this, destino).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    finish()
}

/**
 * Trata o erro de uma chamada: sessão expirada volta ao login, primeiro acesso vai para a troca de senha.
 * Retorna true se já tratou (a tela não precisa mostrar nada).
 */
fun Activity.tratarErroDeSessao(e: Throwable, sessao: Sessao): Boolean {
    if (e !is ErroApi) return false
    return when (e.codigo) {
        "sessao_expirada" -> {
            sessao.sair()
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            irParaProximaEtapa(sessao)
            true
        }
        "primeiro_acesso" -> {
            startActivity(Intent(this, TrocarSenhaActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
            true
        }
        else -> false
    }
}

fun Activity.mensagemDe(e: Throwable): String =
    (e as? ErroApi)?.message ?: "Não foi possível concluir. Tente novamente."

fun Activity.alerta(titulo: String, mensagem: String, aoFechar: (() -> Unit)? = null) {
    if (isFinishing) return
    AlertDialog.Builder(this)
        .setTitle(titulo)
        .setMessage(mensagem)
        .setCancelable(false)
        .setPositiveButton("OK") { _, _ -> aoFechar?.invoke() }
        .show()
}

fun Activity.confirmarSair(sessao: Sessao) {
    AlertDialog.Builder(this)
        .setTitle("Sair")
        .setMessage("Deseja sair da sua conta neste aparelho?")
        .setNegativeButton("Cancelar", null)
        .setPositiveButton("Sair") { _, _ ->
            sessao.sair()
            irParaProximaEtapa(sessao)
        }
        .show()
}

fun View.visivel(v: Boolean) { visibility = if (v) View.VISIBLE else View.GONE }

/** "2026-11-24 10:16:58" → "24/11 10:16" */
fun formatarDataHora(s: String): String {
    val m = Regex("""(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2})""").find(s) ?: return s
    val (_, mes, dia, h, min) = m.destructured
    return "$dia/$mes $h:$min"
}
