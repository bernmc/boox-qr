package com.bernmc.booxqr

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Size
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.zxing.BarcodeFormat
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class MainActivity : ComponentActivity() {

    private lateinit var scanGroup: View
    private lateinit var resultGroup: View
    private lateinit var previewView: PreviewView
    private lateinit var viewfinder: View
    private lateinit var status: TextView
    private lateinit var torchButton: Button
    private lateinit var pickImageButton: Button
    private lateinit var resultKind: TextView
    private lateinit var resultText: TextView
    private lateinit var openButton: Button
    private lateinit var copyButton: Button
    private lateinit var shareButton: Button
    private lateinit var rescanButton: Button
    private lateinit var openError: TextView

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val imageExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null
    private var torchOn = false
    private val handled = AtomicBoolean(false)
    private var permissionPermanentlyDenied = false
    private var inResultState = false
    private var currentText = ""
    private var currentOpenUri: String? = null

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = scanAgain()
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                permissionPermanentlyDenied = false
                status.setText(R.string.status_point)
                startCamera()
            } else {
                permissionPermanentlyDenied =
                    !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
                status.setText(R.string.status_permission)
            }
        }

    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) decodeImage(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scanGroup = findViewById(R.id.scanGroup)
        resultGroup = findViewById(R.id.resultGroup)
        previewView = findViewById(R.id.previewView)
        viewfinder = findViewById(R.id.viewfinder)
        status = findViewById(R.id.status)
        torchButton = findViewById(R.id.torchButton)
        pickImageButton = findViewById(R.id.pickImageButton)
        resultKind = findViewById(R.id.resultKind)
        resultText = findViewById(R.id.resultText)
        openButton = findViewById(R.id.openButton)
        copyButton = findViewById(R.id.copyButton)
        shareButton = findViewById(R.id.shareButton)
        rescanButton = findViewById(R.id.rescanButton)
        openError = findViewById(R.id.openError)

        onBackPressedDispatcher.addCallback(this, backCallback)

        // Viewfinder side = 70% of preview width (decoration only).
        previewView.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
            val side = ((r - l) * 0.7f).toInt()
            val lp = viewfinder.layoutParams
            if (side > 0 && (lp.width != side || lp.height != side)) {
                lp.width = side
                lp.height = side
                viewfinder.layoutParams = lp
            }
        }

        status.setOnClickListener {
            if (!hasCameraPermission()) {
                if (permissionPermanentlyDenied) {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", packageName, null))
                    )
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        }

        torchButton.setOnClickListener {
            val cam = camera ?: return@setOnClickListener
            torchOn = !torchOn
            cam.cameraControl.enableTorch(torchOn)
            updateTorchLabel()
        }

        pickImageButton.setOnClickListener {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        openButton.setOnClickListener { openCurrent() }
        copyButton.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), currentText))
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }
        shareButton.setOnClickListener {
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, currentText)
            startActivity(Intent.createChooser(send, null))
        }
        rescanButton.setOnClickListener { scanAgain() }
        findViewById<Button>(R.id.closeButtonScan).setOnClickListener { finishAndRemoveTask() }
        findViewById<Button>(R.id.closeButtonResult).setOnClickListener { finishAndRemoveTask() }

        if (hasCameraPermission()) {
            status.setText(R.string.status_point)
        } else {
            status.setText(R.string.status_permission)
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        // Covers returning from the settings page after granting permission there.
        if (!inResultState && camera == null && hasCameraPermission()) {
            status.setText(R.string.status_point)
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdown()
        imageExecutor.shutdown()
    }

    private fun hasCameraPermission() =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    // ---- Camera ----

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (inResultState) return@addListener
            val provider = future.get()
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(1280, 720),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()
                )
                .build()

            handled.set(false)
            analysis.setAnalyzer(analysisExecutor) { proxy -> analyze(proxy) }
            imageAnalysis = analysis

            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                )
            } catch (e: Exception) {
                camera = null
                return@addListener
            }

            torchOn = false
            updateTorchLabel()
            torchButton.visibility =
                if (camera?.cameraInfo?.hasFlashUnit() == true) View.VISIBLE else View.GONE
        }, mainExecutor)
    }

    private fun updateTorchLabel() {
        // Label is the action the next tap performs.
        torchButton.setText(if (torchOn) R.string.torch_off else R.string.torch_on)
    }

    private fun stopCamera() {
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        cameraProvider?.unbindAll()
        camera = null
        torchOn = false
        torchButton.visibility = View.GONE
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            if (handled.get()) return
            val plane = proxy.planes[0]
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            val source = PlanarYUVLuminanceSource(
                data, plane.rowStride, proxy.height, 0, 0, proxy.width, proxy.height, false
            )
            val result = Decoder.decode(source, tryHarder = false)
            if (result != null && handled.compareAndSet(false, true)) {
                runOnUiThread { onHit(result) }
            }
        } catch (e: Exception) {
            // Frame dropped; next frame retries.
        } finally {
            proxy.close()
        }
    }

    // ---- From image ----

    private fun decodeImage(uri: Uri) {
        imageExecutor.execute {
            val result = try {
                decodeUri(uri)
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) {
                null
            }
            runOnUiThread {
                if (result != null && handled.compareAndSet(false, true)) {
                    onHit(result)
                } else if (result == null) {
                    Toast.makeText(this, R.string.no_code_in_image, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun decodeUri(uri: Uri): Result? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        bmp.recycle()
        return Decoder.decode(RGBLuminanceSource(w, h, pixels), tryHarder = true)
    }

    // ---- Result state ----

    private fun onHit(result: Result) {
        stopCamera()
        vibrate()
        showResult(result)
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun showResult(result: Result) {
        val raw = result.text.orEmpty()
        val isQr = result.barcodeFormat == BarcodeFormat.QR_CODE
        val parsed = ResultParser.classify(raw, isQr)

        resultKind.text = when (parsed.kind) {
            ResultParser.Kind.URL -> getString(R.string.kind_url)
            ResultParser.Kind.TEXT -> getString(R.string.kind_text)
            ResultParser.Kind.WIFI -> getString(R.string.kind_wifi)
            ResultParser.Kind.EMAIL -> getString(R.string.kind_email)
            ResultParser.Kind.PHONE -> getString(R.string.kind_phone)
            ResultParser.Kind.GEO -> getString(R.string.kind_geo)
            ResultParser.Kind.SMS -> getString(R.string.kind_sms)
            ResultParser.Kind.CONTACT -> getString(R.string.kind_contact)
            ResultParser.Kind.BARCODE -> getString(R.string.kind_barcode, result.barcodeFormat.name)
        }

        val display = StringBuilder()
        if (parsed.kind == ResultParser.Kind.WIFI) {
            parsed.wifiSsid?.let { display.append(getString(R.string.wifi_network, it)).append('\n') }
            parsed.wifiPassword?.let { display.append(getString(R.string.wifi_password, it)).append('\n') }
            display.append('\n')
        }
        display.append(raw)

        currentText = raw
        currentOpenUri = parsed.openUri
        resultText.text = display.toString()
        openButton.visibility = if (parsed.openUri != null) View.VISIBLE else View.GONE
        openError.visibility = View.GONE

        inResultState = true
        backCallback.isEnabled = true
        scanGroup.visibility = View.GONE
        resultGroup.visibility = View.VISIBLE
    }

    private fun openCurrent() {
        val uri = currentOpenUri ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
            openError.visibility = View.GONE
        } catch (e: ActivityNotFoundException) {
            openError.visibility = View.VISIBLE
        }
    }

    private fun scanAgain() {
        inResultState = false
        backCallback.isEnabled = false
        resultGroup.visibility = View.GONE
        scanGroup.visibility = View.VISIBLE
        if (hasCameraPermission()) {
            status.setText(R.string.status_point)
            startCamera()
        } else {
            status.setText(R.string.status_permission)
        }
    }
}
