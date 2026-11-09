package cn.levanll.inventory.ui

import cn.levanll.inventory.i18n.I18n

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 扫码页。
 *
 * 解码路径刻意选择「CameraX 官方 toBitmap() + ZXing RGBLuminanceSource」：
 * 手工解析 Y 平面（rowStride / pixelStride / 旋转）极易出错，
 * 表现为把真实 EAN-13 读成 8 位假码 —— 因为图像被斜切后 ZXing
 * 会在垃圾数据里凑出能通过 EAN-8 校验的假结果，这比直接失败更危险。
 * toBitmap() 由 CameraX 处理全部平面与跨距细节，是这里唯一稳的做法。
 *
 * 代价是每帧多一次位图分配，用「解码节流」抵消：最高约 12 次/秒，
 * 人手持扫码的识别速度远低于此，对体验无影响。
 */
private const val TAG = "ScanScreen"

/** 解码节流间隔：80ms ≈ 12 次/秒 */
private const val DECODE_INTERVAL_MS = 80L

@Composable
fun ScanScreen(
    onScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var lastError by remember { mutableStateOf<String?>(null) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) lastError = I18n.s("未获得相机权限，无法扫码", "未获得相机权限，无法Scan")
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraScanner(onScanned = onScanned, onError = { lastError = it })
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(I18n.s("需要相机权限才能扫码", "Camera permission is required to scan"), color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    I18n.s("重新授权", "Grant again"),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                        .clickable { permLauncher.launch(Manifest.permission.CAMERA) }
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                )
            }
        }

        // 取景框
        if (hasPermission) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                )
            }
        }

        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ArrowBack, I18n.s("返回", "Back"), tint = Color.White)
            }
            Text(I18n.s("扫条码", "Scan barcode"), color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
        }

        // 底部提示
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0x99000000))
                .padding(vertical = 20.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                lastError ?: I18n.s("将商品条码放进取景框，保持 10–20cm 距离", "Place the barcode in the frame, 10-20cm away"),
                color = if (lastError != null) Color(0xFFFF6B54) else Color.White,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun CameraScanner(
    onScanned: (String) -> Unit,
    onError: (String) -> Unit
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val found = remember { AtomicBoolean(false) }
    val lastDecodeAt = remember { AtomicLong(0L) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null

        future.addListener({
            try {
                provider = future.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    // 让 CameraX 把缓冲区物理旋转到竖屏方向。
                    // 不开启时 rotationDegrees 非 0，toBitmap() 出来的图是横的，
                    // ZXing 会在横向图上把 13 位条码切出错误片段。
                    .setOutputImageRotationEnabled(true)
                    .build()

                analysis.setAnalyzer(executor) { proxy ->
                    if (found.get()) {
                        proxy.close()
                        return@setAnalyzer
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastDecodeAt.get() < DECODE_INTERVAL_MS) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    lastDecodeAt.set(now)

                    var code: String? = null
                    try {
                        code = decodeBitmap(proxy)
                    } catch (e: Exception) {
                        Log.d(TAG, "decode failed: ${e.message}")
                    } finally {
                        proxy.close()
                    }

                    if (code != null && found.compareAndSet(false, true)) {
                        Log.d(TAG, "hit=$code")
                        previewView.post { onScanned(code) }
                    }
                }

                provider?.unbindAll()
                provider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "camera start failed", e)
                onError(I18n.s("相机启动失败：${e.message}", "Camera failed: ${e.message}"))
            }
        }, ContextCompat.getMainExecutor(ctx))

        onDispose {
            try {
                provider?.unbindAll()
            } catch (_: Exception) {
            }
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/**
 * 解码一帧。
 *
 * 用 ImageProxy.toBitmap() 让 CameraX 负责 YUV→RGB 与跨距处理，
 * 再交给 ZXing 的 RGBLuminanceSource。这是官方推荐路径。
 *
 * 只给真实商品码格式（EAN-13 / UPC-A / EAN-8 / UPC-E / CODE-128）。
 * 放开 QR / DataMatrix 会显著提高误识别率，对入库场景没收益。
 */
private fun decodeBitmap(proxy: ImageProxy): String? {
    val bmp = proxy.toBitmap()
    val w = bmp.width
    val h = bmp.height
    if (w <= 0 || h <= 0) return null

    val pixels = IntArray(w * h)
    bmp.getPixels(pixels, 0, w, 0, 0, w, h)

    val source = RGBLuminanceSource(w, h, pixels)
    val bitmap = BinaryBitmap(HybridBinarizer(source))

    val reader = MultiFormatReader()
    val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(
            BarcodeFormat.EAN_13,
            BarcodeFormat.UPC_A,
            BarcodeFormat.EAN_8,
            BarcodeFormat.UPC_E,
            BarcodeFormat.CODE_128
        ),
        DecodeHintType.TRY_HARDER to true
    )
    reader.setHints(hints)
    return reader.decodeWithState(bitmap)?.text
}
