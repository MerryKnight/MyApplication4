package com.example.myapplication4
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen()
        }
    }
}

@Composable
fun MainScreen() {
    // Создание состояния для отслеживания текущего экрана
    val isCameraScreen = remember { mutableStateOf(true) }

    // Если текущий экран - камера, показываем экран
    if (isCameraScreen.value) {
        CameraScreen(onNavigateToList = { isCameraScreen.value = false }) // При нажатии переключаем на список
    } else {
        ListScreen(onNavigateToCamera = { isCameraScreen.value = true }) // При нажатии переключаем на камеру
    }
}

@Composable
fun ListScreen(onNavigateToCamera: () -> Unit) {
    val context = LocalContext.current
    val dates = remember { mutableStateListOf<String>() }

    // Загрузка меток времени из папки "photos/date"
    LaunchedEffect(Unit) {
        loadDates(context, dates)
        Log.d("ListScreen", "Loaded dates: ${dates.size}")
    }

    Column(Modifier.fillMaxSize()) {
        Button(onClick = onNavigateToCamera) {
            Text(text = "Switch To Camera")
        }

        if (dates.isEmpty()) {
            Text(
                text = "No images found",
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp)
            )
        } else {
            LazyColumn {
                items(dates) { date ->
                    DateCard(date = date)
                }
            }
        }
    }
}

@Composable
fun DateCard(date: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = date, modifier = Modifier.padding(8.dp))
    }
}

fun File.toBitmap(): Bitmap {
    return BitmapFactory.decodeFile(this.absolutePath)
}

@Composable
fun ImageCard(imageFile: File) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { /* Handler for click if needed */ },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = imageFile.toBitmap().asImageBitmap(),
            contentDescription = imageFile.name, // Исправлено с contentDescribtion на contentDescription
            modifier = Modifier.height(200.dp).fillMaxWidth()
        )
        Text(text = imageFile.name) // Отображение имени файла
    }
}

fun loadDates(context: Context, dates: MutableList<String>) {
    val storageDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "photos/date")
    if (storageDir.exists()) {
        val dateFiles = storageDir.listFiles()?.sortedByDescending { it.lastModified() }
        if (dateFiles != null) {
            dates.clear()
            dates.addAll(dateFiles.map { it.nameWithoutExtension }) // Получаем имена файлов без расширений
        }
    }
}

@Composable
fun CameraScreen(onNavigateToList: () -> Unit) {
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // Объект для контроля за камерой
    val previewView = remember { PreviewView(context) }

    // Используем DisposableEffect для управления ресурсами камеры
    DisposableEffect(Unit) {
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build()
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            imageCapture = ImageCapture.Builder()
                .setTargetResolution(android.util.Size(1440, 1080)) // Меньшее разрешение
                .build()

            // Убедитесь, что все предыдущие привязки сняты
            cameraProvider.unbindAll()

            // Привязываем новый набор к камере
            cameraProvider.bindToLifecycle(
                context as ComponentActivity, cameraSelector, preview, imageCapture
            )

            // Устанавливаем провайдер для предпросмотра
            preview.setSurfaceProvider(previewView.surfaceProvider)
        } catch (e: Exception) {
            Log.e("CameraScreen", "Ошибка инициализации камеры", e)
        }

        onDispose {
            cameraProvider.unbindAll()
        }
    }

    // UI компоненты
    Column(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.weight(1f)
        )

        Button(onClick = { imageCapture?.let { takePhoto(context, it) } }) {
            Text("Capture Photo")
        }

        Button(onClick = { onNavigateToList() }) {
            Text("Switch To List")
        }
    }
}


private fun takePhoto(context: Context, imageCapture: ImageCapture) {
    // Получаем текущую дату и время для имени файла
    val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val photoFileName = "JPEG_$timeStamp.jpg"

    // Создаем путь к каталогу для сохранения
    val storageDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "photos/date")
    if (!storageDir.exists()) {
        storageDir.mkdirs() // Создаем папку, если она не существует
    }

    // Создаем файл с меткой даты и времени
    val photoFile = File(storageDir, photoFileName)

    // Параметры для сохранения изображения
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val msg = "Photo capture succeeded: ${photoFile.absolutePath}"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                Log.d("Camera", msg)
            }

            override fun onError(exc: ImageCaptureException) {
                Log.e("Camera", "Photo capture failed: ${exc.message}", exc)
            }
        }
    )
}