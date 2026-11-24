package com.credenshow.leads.data

import com.credenshow.leads.BuildConfig
import com.google.gson.Gson
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

// ============================================================
// Respostas da API (credenshow.com.br/ApiLeads/)
// Todas têm success / message / codigo.
// ============================================================

open class RespostaBase {
    var success: Boolean = false
    var message: String? = null
    var codigo: String? = null
}

data class Usuario(val id: Int, val nome: String, val email: String, val tipo: String)
data class Evento(val id: Int, val nome: String, val data_inicio: String?, val data_fim: String?)
data class Estande(val id: Int, val codigo: String, val empresa: String, val pavilhao: String?)

data class Lead(
    val id: Int,
    val nome: String,
    val empresa: String,
    val cargo: String,
    val email: String,
    val telefone: String,
    val interesse: String,
    val observacao: String,
    val lido_em: String,
    val coletado_por: String
)

data class Participante(
    val id: Int,
    val nome: String,
    val empresa: String,
    val cargo: String,
    val email: String,
    val telefone: String,
    val ingresso: String
)

class LoginResposta : RespostaBase() {
    var token: String? = null
    var usuario: Usuario? = null
    var primeiro_acesso: Boolean = false
    var eventos: List<Evento> = emptyList()
}

class TokenResposta : RespostaBase() { var token: String? = null }

class EstandesResposta : RespostaBase() { var estandes: List<Estande> = emptyList() }

class DashboardResposta : RespostaBase() {
    var leads_total: Int = 0
    var leads_hoje: Int = 0
    var leads_meus: Int = 0
    var estandes_evento: Int = 0
    var ultimos: List<Lead> = emptyList()
}

class ParticipanteResposta : RespostaBase() {
    var participante: Participante? = null
    var ja_coletado: Boolean = false
    var lead: Lead? = null
}

class SalvarResposta : RespostaBase() {
    var novo: Boolean = false
    var lead_id: Int = 0
}

class ListaResposta : RespostaBase() {
    var total: Int = 0
    var pagina: Int = 1
    var paginas: Int = 1
    var leads: List<Lead> = emptyList()
}

interface LeadsApi {
    @POST("login.php")
    suspend fun login(@Body body: Map<String, String>): LoginResposta

    @POST("trocar_senha.php")
    suspend fun trocarSenha(@Body body: Map<String, String>): TokenResposta

    @GET("estandes.php")
    suspend fun estandes(@Query("evento_id") eventoId: Int): EstandesResposta

    @GET("dashboard.php")
    suspend fun dashboard(@Query("evento_id") eventoId: Int, @Query("estande_id") estandeId: Int): DashboardResposta

    @GET("buscar_participante.php")
    suspend fun buscarParticipante(
        @Query("evento_id") eventoId: Int,
        @Query("estande_id") estandeId: Int,
        @Query("token_qr") tokenQr: String
    ): ParticipanteResposta

    @POST("salvar_lead.php")
    suspend fun salvarLead(@Body body: Map<String, @JvmSuppressWildcards Any>): SalvarResposta

    @GET("listar_leads.php")
    suspend fun listarLeads(
        @Query("evento_id") eventoId: Int,
        @Query("estande_id") estandeId: Int,
        @Query("busca") busca: String? = null,
        @Query("pagina") pagina: Int = 1
    ): ListaResposta
}

/** Erro que a tela mostra ao usuário. codigo = codigo da API ou "sem_conexao"/"timeout"/"servidor". */
class ErroApi(val codigo: String, mensagem: String) : Exception(mensagem)

object Rede {
    private var api: LeadsApi? = null
    private var tokenAtual: String? = null

    fun api(sessao: Sessao): LeadsApi {
        val token = sessao.token
        if (api == null || token != tokenAtual) {
            tokenAtual = token
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                    if (!token.isNullOrEmpty()) req.addHeader("X-Leads-Token", token)
                    chain.proceed(req.build())
                }
                .build()
            api = Retrofit.Builder()
                .baseUrl(BuildConfig.API_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(LeadsApi::class.java)
        }
        return api!!
    }

    /**
     * Executa a chamada e devolve a resposta só se success = true.
     * Qualquer falha vira ErroApi com mensagem pronta para a tela
     * (inclusive 401/403, cujo corpo JSON traz o codigo).
     */
    suspend fun <T : RespostaBase> chamar(bloco: suspend () -> T): T {
        val resposta = try {
            bloco()
        } catch (e: HttpException) {
            val corpo = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
            val base = try { Gson().fromJson(corpo, RespostaBase::class.java) } catch (_: Exception) { null }
            throw ErroApi(
                base?.codigo ?: "servidor",
                base?.message ?: "Erro no servidor (${e.code()}). Tente novamente."
            )
        } catch (e: SocketTimeoutException) {
            throw ErroApi("timeout", "O servidor demorou para responder. Verifique a internet e tente novamente.")
        } catch (e: UnknownHostException) {
            throw ErroApi("sem_conexao", "Sem conexão com a internet.")
        } catch (e: IOException) {
            throw ErroApi("sem_conexao", "Sem conexão com o servidor. Verifique a internet.")
        } catch (e: ErroApi) {
            throw e
        } catch (e: Exception) {
            throw ErroApi("servidor", "Resposta inesperada do servidor. Tente novamente.")
        }
        if (!resposta.success) {
            throw ErroApi(resposta.codigo ?: "erro", resposta.message ?: "Não foi possível concluir. Tente novamente.")
        }
        return resposta
    }
}
