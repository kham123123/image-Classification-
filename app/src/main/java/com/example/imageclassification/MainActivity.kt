package com.example.imageclassification

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.imageclassification.ml.Model
import com.example.imageclassification.ui.theme.ImageClassificationTheme
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImageClassificationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ImageClassificationScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}


@Composable
fun ImageClassificationScreen1(modifier: Modifier) {
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var classificationResult by remember { mutableStateOf("No image selected yet.") }
    var isLoading by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            imageUri = uri
            classificationResult = "Processing..."
            isLoading = uri != null
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }) {
            Text("Pick Image from Gallery")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (imageUri != null) {
            val originalBitmap = remember(imageUri) {
                try {
                    val tempBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(
                            ImageDecoder.createSource(
                                context.contentResolver,
                                imageUri!!
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
                    }

                    // *** FIX IS HERE ***
                    // Force the bitmap configuration to ARGB_8888
                    tempBitmap.copy(Bitmap.Config.ARGB_8888, true)

                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            if (originalBitmap != null) {
                Image(
                    bitmap = originalBitmap.asImageBitmap(),
                    contentDescription = "Selected Image",
                    modifier = Modifier
                        .size(250.dp)
                        .padding(8.dp)
                )


                LaunchedEffect(originalBitmap) {
                    isLoading = true
                    classificationResult = runCatching {
                        // Pass the ARGB_8888 bitmap to the classification function
                        classifyImage(originalBitmap, context)
                    }.getOrElse {
                        "Error during classification: ${it.message}"

                    }
                    isLoading = false
                }
                Log.d("TAG", "ImageClassificationScreen:$classificationResult ")

            } else {
                Text("Error loading image.")
            }
        } else {
            Box(modifier = Modifier.size(250.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Text(
                text = classificationResult,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
fun ImageClassificationScreen(modifier: Modifier) {
    val context = LocalContext.current
    // Change: Use a Bitmap state so both Camera and Gallery can update it
    var bitmapState by remember { mutableStateOf<Bitmap?>(null) }
    var classificationResult by remember { mutableStateOf("No image selected yet.") }
    var isLoading by remember { mutableStateOf(false) }


    // ... other state variables and launchers (cameraLauncher, galleryLauncher) ...



    // 1. Gallery Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                classificationResult = "Processing..."
                isLoading = true
                try {
                    val tempBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                    bitmapState = tempBitmap.copy(Bitmap.Config.ARGB_8888, true)
                } catch (e: Exception) {
                    classificationResult = "Error loading gallery image"
                    isLoading = false
                }
            }
        }
    )

    // 2. Camera Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
        onResult = { bitmap: Bitmap? ->
            if (bitmap != null) {
                classificationResult = "Processing..."
                isLoading = true
                // Camera thumbnails are often RGB_565; convert to ARGB_8888
                bitmapState = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            }
        }
    )


    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, launch camera
            cameraLauncher.launch(null) // Assuming cameraLauncher is defined
        } else {
            // Permission denied
            classificationResult = "Camera permission denied." // Assuming classificationResult is defined
        }
    }

    Column(
        modifier = modifier // Use the modifier passed from Scaffold
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Buttons Row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }) {
                Text("Pick Gallery")
            }

            Button(onClick = {
                val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                    // Permission already granted, launch camera
                    cameraLauncher.launch(null) // Assuming cameraLauncher is defined
                } else {
                    // Request the permission
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }) {
                Text("Take Photo")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display and Process Section
        bitmapState?.let { btm ->
            Image(
                bitmap = btm.asImageBitmap(),
                contentDescription = "Selected Image",
                modifier = Modifier
                    .size(250.dp)
                    .padding(8.dp)
            )

            // Analysis logic triggers whenever bitmapState changes
            LaunchedEffect(btm) {
                isLoading = true
                classificationResult = runCatching {
                    classifyImage(btm, context)
                }.getOrElse {
                    "Error: ${it.message}"
                }
                isLoading = false
            }
        } ?: Box(modifier = Modifier.size(250.dp)) {
            Text("No image selected", modifier = Modifier.align(Alignment.Center))
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Text(
                text = classificationResult,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun classifyImage(bitmap: Bitmap, context: android.content.Context): String {
    // 1. Initialize your specific model class
    val model = Model.newInstance(context)

    // 2. Preprocess the image to 224x224 and convert to TensorImage
    val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
        // Optional: Add NormalizeOp if your model was trained with normalization
        // .add(NormalizeOp(0f, 255f))
        .build()

    var tensorImage = TensorImage(DataType.FLOAT32)
    tensorImage.load(bitmap)
    tensorImage = imageProcessor.process(tensorImage)

    // 3. Create inputs for reference (matches your provided snippet)
    val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)
    inputFeature0.loadBuffer(tensorImage.buffer)

    // 4. Run model inference and get result
    val outputs = model.process(inputFeature0)
    val outputFeature0 = outputs.outputFeature0AsTensorBuffer

    // 5. Post-process: Convert float array results into labels
    val scores = outputFeature0.floatArray

    // Load labels (ensure you have labels.txt in your assets folder)
    val labels = context.assets.open("labels.txt").bufferedReader().useLines { it.toList() }

    // Find index of highest score
    val maxIdx = scores.indices.maxByOrNull { scores[it] } ?: -1
    val resultText = if (maxIdx != -1 && maxIdx < labels.size) {
        "Result: ${labels[maxIdx]} (${"%.2f".format(scores[maxIdx] * 100)}%)"
    } else {
        "Unknown result"
    }

    // 6. Release model resources
    model.close()

    return resultText
}
