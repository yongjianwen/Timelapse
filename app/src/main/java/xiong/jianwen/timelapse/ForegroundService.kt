package xiong.jianwen.timelapse

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import xiong.jianwen.timelapse.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ForegroundService : LifecycleService() {

    @SuppressLint("NotificationPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        cameraExecutor = Executors.newSingleThreadExecutor()

        Log.d("MainActivity", "onStartCommand")
        val time = Date()
        val timeStr = Date().toString()
        var runCount = 1
        val gapInSeconds = 30L
        var prevTimeStr = Date().toString()

        val CHANNEL_ID = "Test Foreground Service ID"
        val notificationChannel =
            NotificationChannel(CHANNEL_ID, CHANNEL_ID, NotificationManager.IMPORTANCE_DEFAULT)

        getSystemService(NotificationManager::class.java).createNotificationChannel(
            notificationChannel
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
        val notification =
            builder.setContentText("Capturing $runCount/1\nLast Captured: $prevTimeStr\nStart Time: $timeStr")
                .setContentTitle("Capturing Timelapse").setSmallIcon(
                    R.drawable.ic_launcher_background
                )

        startForeground(1001, notification.build())

        Thread {
            Thread.sleep(1000 * gapInSeconds)

            try {
                while (true) {
                    val now = Date()
                    val durationInSeconds = TimeUnit.MILLISECONDS.toSeconds(now.time - time.time)
                    val expectedRunCount = (durationInSeconds / gapInSeconds).toInt() - 1
                    val msg =
                        "Capturing $runCount/$expectedRunCount\nLast Captured: $prevTimeStr\nStart Time: $timeStr"
                    builder.setContentText(msg)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(msg))

                    val notificationManager: NotificationManager =
                        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(1001, builder.build())

                    prevTimeStr = now.toString()
                    runCount += 1

                    startCamera()

                    Thread.sleep(1000 * gapInSeconds)
                }
            } catch (e: InterruptedException) {
                Log.d("test: ", "interrupted")
            }
        }.start()

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onDestroy()
    }

//    override fun onBind(p0: Intent?): IBinder? {
//        return null
//    }

    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    private fun startCamera() {
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

    }
}