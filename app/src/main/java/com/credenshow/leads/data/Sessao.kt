package com.credenshow.leads.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Dados do login guardados no aparelho (token, usuário, evento e estande escolhidos). */
class Sessao(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("leads_sessao", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString("token", null)
        set(v) = prefs.edit().putString("token", v).apply()

    var usuarioNome: String
        get() = prefs.getString("usuario_nome", "") ?: ""
        set(v) = prefs.edit().putString("usuario_nome", v).apply()

    var ultimoEmail: String
        get() = prefs.getString("ultimo_email", "") ?: ""
        set(v) = prefs.edit().putString("ultimo_email", v).apply()

    var eventos: List<Evento>
        get() = prefs.getString("eventos", null)?.let {
            try { Gson().fromJson<List<Evento>>(it, object : TypeToken<List<Evento>>() {}.type) } catch (_: Exception) { null }
        } ?: emptyList()
        set(v) = prefs.edit().putString("eventos", Gson().toJson(v)).apply()

    var eventoId: Int
        get() = prefs.getInt("evento_id", 0)
        set(v) = prefs.edit().putInt("evento_id", v).apply()

    var eventoNome: String
        get() = prefs.getString("evento_nome", "") ?: ""
        set(v) = prefs.edit().putString("evento_nome", v).apply()

    var estandeId: Int
        get() = prefs.getInt("estande_id", 0)
        set(v) = prefs.edit().putInt("estande_id", v).apply()

    var estandeNome: String
        get() = prefs.getString("estande_nome", "") ?: ""
        set(v) = prefs.edit().putString("estande_nome", v).apply()

    /** Quantos estandes o usuário tem no evento atual (para mostrar "Trocar estande"). */
    var qtdEstandesUsuario: Int
        get() = prefs.getInt("qtd_estandes_usuario", 1)
        set(v) = prefs.edit().putInt("qtd_estandes_usuario", v).apply()

    val logado: Boolean get() = !token.isNullOrEmpty()
    val pronto: Boolean get() = logado && eventoId > 0 && estandeId > 0

    fun limparEstande() {
        prefs.edit().remove("estande_id").remove("estande_nome").apply()
    }

    fun limparEvento() {
        limparEstande()
        prefs.edit().remove("evento_id").remove("evento_nome").apply()
    }

    /** Sai do app: apaga tudo, menos o último e-mail digitado. */
    fun sair() {
        val email = ultimoEmail
        prefs.edit().clear().apply()
        ultimoEmail = email
    }
}
