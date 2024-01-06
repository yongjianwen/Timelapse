package xiong.jianwen.timelapse

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import xiong.jianwen.timelapse.databinding.ActivityMainBinding
import xiong.jianwen.timelapse.utils.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ExecutorService
import kotlin.math.roundToInt

class ForegroundService : LifecycleService() {

    private lateinit var wakeLock: PowerManager.WakeLock

    companion object {
        private const val TAG = "ForegroundService"
    }

    @SuppressLint("NotificationPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        /*// viewBinding = ActivityMainBinding.inflate(layoutInflater)
        cameraExecutor = Executors.newSingleThreadExecutor()*/
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag").apply {
                acquire(1 * 24 * 60 * 60 * 1000L)    // 1 day
            }
        }

        val intervalInSeconds = 5L
        val startTime = System.currentTimeMillis()
        var runCount = 0

        val title = "Capturing Timelapse"
        var msg = "Starting to capture"
        val builder = NotificationService.buildTestNotification(this, title, msg, msg)
        val prevTime = 0L

        startForeground(1001, builder.build())

        Thread {
            try {
                while (true) {
                    ++runCount
                    val nowTime = System.currentTimeMillis()
                    val expectedCount = ((nowTime - startTime) / 5000).toInt() + 1
                    val successRate =
                        (runCount * 100.0 / expectedCount * 100.0).roundToInt() / 100.0
                    val successRateStr = String.format("%.2f", successRate)
                    val missingCount = expectedCount - runCount

                    val shortMsg =
                        "Capturing $runCount/$expectedCount - $successRateStr% (missing $missingCount)"
                    msg = "$shortMsg\nStarted from $startTime"
                    NotificationService.triggerTestNotification(builder, this, title, shortMsg, msg)

                    if (!Constants.testNotiOnly) {
                        startCamera()
                    }

                    // Test delivery methods: Queued delivery vs Timed delivery
                    /* Test results:
                       Queued delivery enqueues the next trigger after the last successful trigger by
                       the specified interval, causing delays to accumulate and eventually falling
                       short of the total expected trigger count.
                       Timed delivery enqueues the next trigger after the last successful trigger by
                       a flexible interval, which is calculated by finding the difference between the
                       next expected trigger time and the current time, to offset the delays caused by
                       the process itself.
                    */

                    // Queued delivery
                    /*Thread.sleep(1000 * intervalInSeconds)*/

                    // Timed delivery
                    var nextTrigger = startTime + runCount * 1000 * intervalInSeconds
                    if (nextTrigger <= System.currentTimeMillis()) {
                        while (nextTrigger <= System.currentTimeMillis()) {
                            nextTrigger += runCount * 1000 * intervalInSeconds
                        }
                    }
                    Thread.sleep(nextTrigger - System.currentTimeMillis())
                }
            } catch (e: InterruptedException) {
                Log.e(TAG, e.stackTrace.toString())
            }
        }.start()

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        // Original
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        wakeLock.release()

        // Test restart service
        /*val serviceIntent = Intent(this.applicationContext, ForegroundService::class.java)
        startForegroundService(serviceIntent)*/

        super.onDestroy()
    }

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