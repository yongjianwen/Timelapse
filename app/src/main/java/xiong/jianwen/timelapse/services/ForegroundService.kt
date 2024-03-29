package xiong.jianwen.timelapse.services

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import xiong.jianwen.timelapse.R
import xiong.jianwen.timelapse.databinding.ActivityMainBinding
import xiong.jianwen.timelapse.utils.Constants
import xiong.jianwen.timelapse.utils.UserPreferences
import xiong.jianwen.timelapse.utils.Utilities
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt

class ForegroundService : LifecycleService(), CoroutineScope {

    private var job: Job = Job()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private lateinit var wakeLock: PowerManager.WakeLock

    private lateinit var t: Thread

    var runCount = 0
    var expectedCount = 0

    companion object {
        private const val TAG = "ForegroundService"
    }

    @SuppressLint("NotificationPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent == null) {
            Toast.makeText(applicationContext, "Timelapse: No intent passed!", Toast.LENGTH_LONG)
                .show()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // Acquire partial wake lock
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Timelapse::MyWakelockTag").apply {
                acquire(Constants.WAKE_LOCK_TIMEOUT)
            }
        }

        val intervalInSeconds = intent.getIntExtra(Constants.INTERVAL, -1)
        val durationInSeconds = intent.getIntExtra(Constants.DURATION, -1)
        /*Toast.makeText(
            applicationContext,
            "interval: $intervalInput, duration: $durationInput",
            Toast.LENGTH_SHORT
        ).show()*/

        val userPreferences = UserPreferences(this)
        var isMuted = true
        /*lifecycleScope.launch {
            userPreferences.intervalFlow.collect {
                interval = it
            }
        }
        lifecycleScope.launch {
            userPreferences.durationFlow.collect {
                duration = it
            }
        }*/
        lifecycleScope.launch {
            userPreferences.isMutedFlow.collect {
                isMuted = it
            }
        }

        // viewBinding = ActivityMainBinding.inflate(layoutInflater)
        cameraExecutor = Executors.newSingleThreadExecutor()

        val startTime = System.currentTimeMillis()
        // var runCount = 0

        val notiTitle = resources.getString(R.string.noti_title)
        val builder = NotificationService.buildTestNoti(
            this,
            notiTitle,
            resources.getString(R.string.noti_short_msg_init),
            resources.getString(R.string.noti_short_msg_init)
        )

        startForeground(1001, builder.build())

        t = Thread {
            try {
                while (true) {
                    // Need to call Looper.getMainLooper() to Toast
                    /*Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            applicationContext,
                            "interval: $interval, duration: $duration, is muted: $isMuted",
                            Toast.LENGTH_SHORT
                        ).show()
                    }*/

                    ++runCount
                    val nowTime = System.currentTimeMillis()
                    // val expectedCount = ((nowTime - startTime) / 5000).toInt() + 1
                    expectedCount = ((nowTime - startTime) / 5000).toInt() + 1
                    val successRate =
                        (runCount * 100.0 / expectedCount * 100.0).roundToInt() / 100.0
                    val successRateStr = String.format("%.2f", successRate)
                    val missingCount = expectedCount - runCount

                    if (this::progressObserver.isInitialized) {
                        progressObserver.onNext(runCount.toFloat())
                    }

                    val shortMsg = resources.getString(
                        R.string.noti_short_msg,
                        runCount,
                        expectedCount,
                        successRateStr,
                        missingCount
                    )
                    val msg = resources.getString(R.string.noti_long_msg, shortMsg, startTime)
                    NotificationService.triggerNoti(builder, this, notiTitle, shortMsg, msg)

//                    if (!Constants.NO_CAMERA) {
                    // startCamera()
//                    }

                    if (!isMuted) {
                        Utilities.playSound()
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

                    if (System.currentTimeMillis() - startTime > durationInSeconds * 1000) {
                        val a = NotificationService.buildCompletedNoti(
                            applicationContext,
                            "Ended!",
                            "Service completed",
                            "Service completed"
                        )
                        a.build()
                        NotificationService.triggerNoti(
                            a,
                            this,
                            "Ended!",
                            "Service completed",
                            "Service completed"
                        )
                        throw InterruptedException()
                    }
                }
            } catch (e: InterruptedException) {
//                test()
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
                e.printStackTrace()
            }
        }

        t.start()

        return START_NOT_STICKY
    }

    fun test() {
        // java.lang.NullPointerException: Can't toast on a thread that has not called Looper.prepare()
        /*Toast.makeText(
            applicationContext,
            "Ended!",
            Toast.LENGTH_SHORT
        ).show()*/

        val a = NotificationService.buildCompletedNoti(
            applicationContext,
            "Ended!",
            "Service completed",
            "Service completed"
        )
        a.build()
        NotificationService.triggerNoti(a, this, "Ended!", "Service completed", "Service completed")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSelf()
        wakeLock.release()
    }

    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraProvider: ProcessCameraProvider

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            // val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            cameraProvider = cameraProviderFuture.get()

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
                e.printStackTrace()
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
                    cameraProvider.unbindAll()
                }

                override fun onError(exception: ImageCaptureException) {
//                    Log.e(MainActivity.TAG, "Photo capture failed: ${exception.message}", exception)
                    cameraProvider.unbindAll()
                }
            })
    }

    private val mBinder: IBinder = MyBinder()
    private lateinit var progressObserver: ObservableEmitter<Float>
    private lateinit var observableProgress: Observable<Float>

    inner class MyBinder : Binder() {
        fun getService(): ForegroundService {
            return this@ForegroundService
        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return mBinder
    }

    fun streamToMain(view: Preview.SurfaceProvider) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(view) }
            imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    fun observeProgress(): Observable<Float> {
        if (!this::observableProgress.isInitialized) {
            observableProgress = Observable.create { emitter -> progressObserver = emitter }
            observableProgress = observableProgress.share()
        }
        return observableProgress
    }
}