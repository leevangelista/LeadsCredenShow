package com.credenshow.leads.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.credenshow.leads.data.Rede
import com.credenshow.leads.data.Sessao
import com.credenshow.leads.databinding.ActivityScannerBinding
import com.google.gson.Gson
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Lê o QR Code da credencial (o mesmo do evento) e abre a tela do lead.
 * Ao voltar da tela do lead, o leitor continua ligado para o próximo visitante.
 */
class ScannerActivity : AppCompatActivity() {

    private lateinit var b: ActivityScannerBinding
    private lateinit var sessao: Sessao
    private lateinit var cameraExecutor: ExecutorService
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    )

    @Volatile private var lendo = true
    private var ultimoQr: String? = null
    private var liberadoEm = 0L

    private val pedirCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) iniciarCamera() else {
            Toast.makeText(this, "Permita o uso da câmera para ler os QR Codes.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(b.root)
        sessao = Sessao(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        b.tvEstande.text = sessao.estandeNome
        b.btnVoltar.setOnClickListener { finish() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            iniciarCamera()
        } else {
            pedirCamera.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        liberar()
    }

    private fun iniciarCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(b.previewView.surfaceProvider) }
            val analise = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor) { img -> analisar(img) } }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analise)
            } catch (e: Exception) {
                Log.e("Scanner", "Falha ao abrir a câmera", e)
                alerta("Câmera indisponível", "Não foi possível abrir a câmera deste aparelho.") { finish() }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analisar(img: ImageProxy) {
        val media = img.image
        if (!lendo || media == null) { img.close(); return }

        scanner.process(InputImage.fromMediaImage(media, img.imageInfo.rotationDegrees))
            .addOnSuccessListener { codigos ->
                val valor = codigos.firstNotNullOfOrNull { it.rawValue?.takeIf { v -> v.isNotBlank() } }
                if (valor != null && lendo && !mesmoQrRecente(valor)) {
                    lendo = false
                    ultimoQr = valor
                    buscar(valor)
                }
            }
            .addOnCompleteListener { img.close() }
    }

    private fun buscar(tokenQr: String) {
        b.progress.visivel(true)
        b.tvStatus.text = "Buscando visitante..."
        lifecycleScope.launch {
            try {
                val r = Rede.chamar {
                    Rede.api(sessao).buscarParticipante(sessao.eventoId, sessao.estandeId, tokenQr.trim())
                }
                b.progress.visivel(false)
                startActivity(Intent(this@ScannerActivity, LeadActivity::class.java)
                    .putExtra(LeadActivity.EXTRA_TOKEN_QR, tokenQr.trim())
                    .putExtra(LeadActivity.EXTRA_DADOS, Gson().toJson(r)))
            } catch (e: Exception) {
                b.progress.visivel(false)
                if (tratarErroDeSessao(e, sessao)) return@launch
                alerta("QR Code não aceito", mensagemDe(e)) { liberar() }
            }
        }
    }

    private fun liberar() {
        liberadoEm = SystemClock.elapsedRealtime()
        b.progress.visivel(false)
        b.tvStatus.text = "Aponte a câmera para o QR Code da credencial"
        lendo = true
    }

    // Ignora o mesmo QR logo após voltar (a credencial ainda está na frente da câmera)
    private fun mesmoQrRecente(qr: String) =
        qr == ultimoQr && SystemClock.elapsedRealtime() - liberadoEm < 4_000L

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        scanner.close()
    }
}
