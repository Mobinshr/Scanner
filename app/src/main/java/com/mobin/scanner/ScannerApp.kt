package com.mobin.scanner

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun ScannerApp() {
    val context = LocalContext.current
    val scanLock = remember { AtomicBoolean(false) }
    val scanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }
    val history = remember {
        mutableStateListOf<ScanRecord>().apply {
            addAll(ScanHistoryStore.load(context))
        }
    }

    var selectedTab by remember { mutableStateOf(0) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var torchEnabled by remember { mutableStateOf(false) }
    var activeResult by remember { mutableStateOf<ScanRecord?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun acceptResult(value: String, format: String) {
        val record = ScanRecord(value = value, format = format)
        history.removeAll { it.value == value }
        history.add(0, record)
        while (history.size > ScanHistoryStore.limit) {
            history.removeAt(history.lastIndex)
        }
        ScanHistoryStore.save(context, history.toList())
        activeResult = record
        selectedTab = 0
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("برای اسکن زنده، اجازه دسترسی به دوربین لازم است.")
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { imageUri ->
        if (imageUri != null) {
            scanLock.set(true)
            try {
                val inputImage = InputImage.fromFilePath(context, imageUri)
                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        val barcode = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                        val value = barcode?.rawValue
                        if (value != null) {
                            acceptResult(value, "QR Code")
                        } else {
                            scanLock.set(false)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("کد QR در این تصویر پیدا نشد.")
                            }
                        }
                    }
                    .addOnFailureListener {
                        scanLock.set(false)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("خواندن تصویر ناموفق بود. تصویر دیگری را امتحان کن.")
                        }
                    }
            } catch (_: Exception) {
                scanLock.set(false)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("امکان باز کردن این تصویر وجود ندارد.")
                }
            }
        }
    }

    LaunchedEffect(activeResult) {
        if (activeResult == null) {
            scanLock.set(false)
            torchEnabled = false
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
                        label = { Text("اسکن") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Filled.History, contentDescription = null) },
                        label = { Text("تاریخچه") }
                    )
                }
            }
        ) { innerPadding ->
            if (selectedTab == 0) {
                ScannerHome(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    hasCameraPermission = hasCameraPermission,
                    torchEnabled = torchEnabled,
                    scanner = scanner,
                    scanLock = scanLock,
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onGalleryClick = { galleryLauncher.launch("image/*") },
                    onTorchClick = { torchEnabled = !torchEnabled },
                    onDetected = ::acceptResult,
                    onHistoryClick = { selectedTab = 1 }
                )
            } else {
                ScannerHistory(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    history = history,
                    onItemClick = { activeResult = it },
                    onDelete = { record ->
                        history.remove(record)
                        ScanHistoryStore.save(context, history.toList())
                    },
                    onClear = {
                        history.clear()
                        ScanHistoryStore.save(context, emptyList())
                    }
                )
            }
        }

        activeResult?.let { record ->
            ResultSheet(
                record = record,
                onDismiss = { activeResult = null }
            )
        }
    }
}

@Composable
private fun ScannerHome(
    modifier: Modifier = Modifier,
    hasCameraPermission: Boolean,
    torchEnabled: Boolean,
    scanner: BarcodeScanner,
    scanLock: AtomicBoolean,
    onRequestPermission: () -> Unit,
    onGalleryClick: () -> Unit,
    onTorchClick: () -> Unit,
    onDetected: (String, String) -> Unit,
    onHistoryClick: () -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp).size(28.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = "Scanner",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "اسکن سریع و آسان کدهای QR",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onHistoryClick) {
                Icon(Icons.Filled.History, contentDescription = "تاریخچه")
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF111111))
        ) {
            if (hasCameraPermission) {
                CameraScannerView(
                    scanner = scanner,
                    torchEnabled = torchEnabled,
                    scanLock = scanLock,
                    onDetected = onDetected,
                    modifier = Modifier.fillMaxSize()
                )
                ScanFrame(modifier = Modifier.fillMaxSize())
                Text(
                    text = "کد QR را داخل کادر قرار دهید",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 28.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "برای اسکن QR به دوربین نیاز داریم",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "دسترسی دوربین فقط برای خواندن کدها استفاده می‌شود.",
                        color = Color(0xFFDADADA),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = onRequestPermission) {
                        Text("اجازه دسترسی به دوربین")
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = onGalleryClick,
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("انتخاب از گالری")
            }
            FilledTonalButton(
                onClick = onTorchClick,
                enabled = hasCameraPermission,
                modifier = Modifier.height(52.dp)
            ) {
                Icon(
                    imageVector = if (torchEnabled) Icons.Filled.FlashOff else Icons.Filled.FlashOn,
                    contentDescription = null
                )
                Spacer(Modifier.size(6.dp))
                Text(if (torchEnabled) "خاموش" else "چراغ‌قوه")
            }
        }

        Text(
            text = "کد QR را واضح و کامل در قاب نگه دارید.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ScanFrame(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val frameWidth = size.width * 0.72f
        val frameHeight = size.height * 0.42f
        val left = (size.width - frameWidth) / 2f
        val top = (size.height - frameHeight) / 2f
        val right = left + frameWidth
        val bottom = top + frameHeight
        val corner = 34.dp.toPx()
        val path = Path().apply {
            moveTo(left, top + corner)
            lineTo(left, top)
            lineTo(left + corner, top)

            moveTo(right - corner, top)
            lineTo(right, top)
            lineTo(right, top + corner)

            moveTo(left, bottom - corner)
            lineTo(left, bottom)
            lineTo(left + corner, bottom)

            moveTo(right - corner, bottom)
            lineTo(right, bottom)
            lineTo(right, bottom - corner)
        }
        drawPath(
            path = path,
            color = Color.White,
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun ScannerHistory(
    modifier: Modifier = Modifier,
    history: List<ScanRecord>,
    onItemClick: (ScanRecord) -> Unit,
    onDelete: (ScanRecord) -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "تاریخچه اسکن",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${history.size} مورد ذخیره‌شده",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (history.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text("پاک‌کردن همه")
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "هنوز چیزی اسکن نکرده‌اید",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "نتایج اسکن‌های شما اینجا ذخیره می‌شوند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history, key = { it.id }) { record ->
                    HistoryCard(
                        record = record,
                        onClick = { onItemClick(record) },
                        onDelete = { onDelete(record) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    record: ScanRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Filled.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(10.dp).size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = record.value,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.getDefault())
                        .format(Date(record.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "حذف نتیجه",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ResultSheet(
    record: ScanRecord,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val webUrl = toWebUrl(record.value)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp).size(24.dp)
                    )
                }
                Column(modifier = Modifier.padding(horizontal = 12.dp).weight(1f)) {
                    Text(
                        "نتیجه اسکن",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        record.format,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                SelectionContainer {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = record.value,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("QR result", record.value))
                        Toast.makeText(context, "کپی شد", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("کپی")
                }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, record.value)
                        }
                        context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری نتیجه"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("اشتراک‌گذاری")
                }
            }

            if (webUrl != null) {
                Button(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
                        } catch (_: Exception) {
                            Toast.makeText(context, "برنامه‌ای برای باز کردن لینک پیدا نشد.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("باز کردن لینک")
                }
            }
        }
    }
}

private fun toWebUrl(value: String): String? {
    val trimmed = value.trim()
    return when {
        trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
        trimmed.startsWith("www.", ignoreCase = true) -> "https://$trimmed"
        else -> null
    }
}
