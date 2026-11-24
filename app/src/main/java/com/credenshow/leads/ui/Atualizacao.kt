package com.credenshow.leads.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Atualização do app pelo servidor (mesmo esquema do app de credenciamento):
 * lê credenshow.com.br/app/leads/versao.json e, se o versionCode de lá for maior que o instalado,
 * baixa o APK e abre o instalador do Android. A atualização é obrigatória (aviso sem "Depois").
 * Sem internet não trava. Só roda na versão release (a debug tem outra assinatura).
 * O versao.json e o APK são publicados por publicar-atualizacao.sh (ou pelo GitHub Actions).
 */
object Atualizacao {

    private const val TAG = "Atualizacao"
    private const val URL_VERSAO = "https://credenshow.com.br/app/leads/versao.json"
    private const val INTERVALO_VERIFICACAO_MS = 10 * 60_000L

    private var ultimaVerificacao = 0L
    private var verificando = false
    private var pendente: VersaoServidor? = null
    private var avisoAberto: AlertDialog? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private data class VersaoServidor(val versionCode: Long, val versionName: String, val urlApk: String)

    /** Consulta o servidor no máximo a cada 10 min; chame no onResume das telas principais. */
    fun verificar(activity: AppCompatActivity) {
        val debuggable = activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) return

        // Voltou da configuração de permissão ou cancelou o instalador: trava de novo
        pendente?.let {
            if (it.versionCode > versaoInstalada(activity)) {
                exigirAtualizacao(activity, it)
                return
            }
            pendente = null
        }
        if (verificando) return

        val agora = SystemClock.elapsedRealtime()
        if (ultimaVerificacao != 0L && agora - ultimaVerificacao < INTERVALO_VERIFICACAO_MS) return
        ultimaVerificacao = agora
        verificando = true

        activity.lifecycleScope.launch {
            try {
                val servidor = withContext(Dispatchers.IO) { buscarVersaoServidor() }
                if (servidor.versionCode <= versaoInstalada(activity)) return@launch
                pendente = servidor
                exigirAtualizacao(activity, servidor)
            } catch (e: Exception) {
                Log.w(TAG, "Não foi possível verificar atualização", e)
            } finally {
                verificando = false
            }
        }
    }

    private fun exigirAtualizacao(activity: AppCompatActivity, servidor: VersaoServidor) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (avisoAberto?.let { it.isShowing && it.ownerActivity === activity } == true) return

        avisoAberto = AlertDialog.Builder(activity)
            .setTitle("Atualização obrigatória")
            .setMessage(
                "A versão ${servidor.versionName} do CredenShow Leads está disponível. " +
                    "Atualize para continuar usando o app."
            )
            .setCancelable(false)
            .setPositiveButton("Atualizar") { _, _ -> baixarEInstalar(activity, servidor) }
            .show()
    }

    private fun buscarVersaoServidor(): VersaoServidor {
        val request = Request.Builder().url(URL_VERSAO).cacheControl(CacheControl.FORCE_NETWORK).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} em $URL_VERSAO")
            val json = JSONObject(response.body?.string().orEmpty())
            return VersaoServidor(
                versionCode = json.getLong("versionCode"),
                versionName = json.optString("versionName"),
                // O caminho do APK é relativo ao versao.json
                urlApk = URL_VERSAO.toHttpUrl().resolve(json.getString("apk")).toString()
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun versaoInstalada(activity: AppCompatActivity): Long {
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    }

    private fun baixarEInstalar(activity: AppCompatActivity, servidor: VersaoServidor) {
        // Android 8+: o usuário precisa permitir uma vez que o app instale atualizações
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            avisoAberto = AlertDialog.Builder(activity)
                .setTitle("Permissão necessária")
                .setMessage(
                    "Para instalar a atualização, ative \"Permitir desta fonte\" para o CredenShow Leads " +
                        "na próxima tela e depois volte ao app."
                )
                .setCancelable(false)
                .setPositiveButton("Abrir configuração") { _, _ ->
                    activity.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
                    )
                }
                .show()
            return
        }

        val progresso = AlertDialog.Builder(activity)
            .setTitle("Baixando atualização...")
            .setView(ProgressBar(activity).apply { isIndeterminate = true; setPadding(0, 48, 0, 48) })
            .setCancelable(false)
            .show()

        activity.lifecycleScope.launch {
            try {
                val apk = withContext(Dispatchers.IO) { baixarApk(activity, servidor.urlApk) }
                progresso.dismiss()
                abrirInstalador(activity, apk)
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao baixar atualização", e)
                progresso.dismiss()
                if (activity.isFinishing || activity.isDestroyed) return@launch
                avisoAberto = AlertDialog.Builder(activity)
                    .setTitle("Não foi possível baixar a atualização")
                    .setMessage(
                        if (e is IOException) "Sem conexão com o servidor. Verifique a internet e tente novamente."
                        else "Erro ao baixar o arquivo. Tente novamente."
                    )
                    .setCancelable(false)
                    .setPositiveButton("Tentar novamente") { _, _ -> baixarEInstalar(activity, servidor) }
                    .show()
            }
        }
    }

    private fun baixarApk(activity: AppCompatActivity, url: String): File {
        // Fica no cacheDir, liberado no FileProvider (res/xml/file_paths.xml)
        val pasta = File(activity.cacheDir, "atualizacao").apply { mkdirs() }
        val destino = File(pasta, "leads.apk")
        val temporario = File(pasta, "leads.apk.parcial")

        val request = Request.Builder().url(url).cacheControl(CacheControl.FORCE_NETWORK).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} ao baixar o APK")
            val body = response.body ?: error("APK vazio")
            temporario.outputStream().use { saida -> body.byteStream().copyTo(saida) }
        }

        if (!temporario.renameTo(destino)) {
            temporario.copyTo(destino, overwrite = true)
            temporario.delete()
        }
        return destino
    }

    private fun abrirInstalador(activity: AppCompatActivity, apk: File) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
    }
}
