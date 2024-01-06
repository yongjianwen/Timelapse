// File not in use
package xiong.jianwen.timelapse

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import androidx.camera.core.ImageCapture
import java.util.concurrent.ExecutorService
import kotlin.math.roundToInt

class MyBroadcastReceiver : BroadcastReceiver() {

    lateinit var mediaPlayer: MediaPlayer
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var num = 0

    override fun onReceive(context: Context?, intent: Intent?) {
//        WakeLocker.acquire(context!!)

        // Play sound
        /*mediaPlayer = MediaPlayer.create(context, R.raw.camera)
        mediaPlayer.start()*/

        val runCount = intent!!.getIntExtra("runCount", -1)
        val expectedCount = intent.getIntExtra("expectedCount", -1)
        val successRate = (runCount * 100.0 / expectedCount * 100.0).roundToInt() / 100.0
        val successRateStr = String.format("%.2f", successRate)
        val missingCount = expectedCount - runCount
        val startTime = intent.getLongExtra("startTime", -1L)

        // Trigger notification
        val title = "Capturing Timelapse"
        val shortMsg =
            "Capturing $runCount/$expectedCount - $successRateStr% (missing $missingCount)"
        val msg = "$shortMsg\nStarted from $startTime"
//        NotificationService.triggerTestNotification(context!!, title, shortMsg, msg)

        MainActivity.setAlarm()

//        cameraExecutor = Executors.newSingleThreadExecutor()

//        startCamera()
//        WakeLocker.release()
    }

    /*private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
//            val preview = Preview.Builder().build()
//                .also { it.setSurfaceProvider(viewBinding.previewViewCamera.surfaceProvider) }
            val preview = null

            imageCapture = ImageCapture.Builder().build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture)
//                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                takePhoto()
            } catch (e: Exception) {
                Log.e("ForegroundService", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        // This will be null if photo button is clicked before image capture is set up
        val imageCapture = imageCapture ?: return

        // Create file name and MediaStore entry
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss")
        val dateStr = dateFormat.format(Date())
        val name = "IMG_$dateStr"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Timelapse")
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        // Set up image capture listener, which is triggered after photo has been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${outputFileResults.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
//                    Log.d(MainActivity.TAG, msg)
                    cameraExecutor.shutdown()
                }

                override fun onError(exception: ImageCaptureException) {
//                    Log.e(MainActivity.TAG, "Photo capture failed: ${exception.message}", exception)
                    cameraExecutor.shutdown()
                }
            })

    }*/
}