package xiong.jianwen.timelapse

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import xiong.jianwen.timelapse.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {

    init {
        instance = this
    }

    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value) permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext, "Permission request denied", Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    companion object {
        private const val TAG = "MainActivity"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(android.Manifest.permission.CAMERA).toTypedArray()
        private var instance: MainActivity? = null
        private val startTime = System.currentTimeMillis()
        private var runCount = 0
        private var prevTime = 0L

        fun applicationContext(): Context {
            return instance!!.applicationContext
        }

        @SuppressLint("BatteryLife")
        fun setAlarm() {
            val nowTime = System.currentTimeMillis()
            val expectedCount = ((nowTime - startTime) / 5000).toInt() + 1

            val intent = Intent(applicationContext(), MyBroadcastReceiver::class.java)
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.putExtra("runCount", ++runCount)
            intent.putExtra("expectedCount", expectedCount)
            intent.putExtra("startTime", startTime)
            val pendingIntent = PendingIntent.getBroadcast(
                applicationContext(), 234324243, intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = applicationContext().getSystemService(ALARM_SERVICE) as AlarmManager

            // set(): Need to use AlarmManager.RTC_WAKEUP in order for alarm to continue working
            // while screen is turned off
            // It was observed that although alarm is consistently triggered (with RTC_WAKEUP,
            // screen on/off, charging) (proven by sound played), Toast may not display each time alarm is triggered
            // and that the alarm will stop working once the app is killed (the last alarm scheduled
            // before app is killed wil be triggered
            // When not charging (and battery optimization turned on), alarm will stop working after a short while (< 10)
            // Test alarm manager with battery optimization off (charging on and off, app not killed)
            // "Frequently wakes system" warning by system
            /* Test results:
               In a span of 2.8 days, and set to be running every 5 seconds, number of times
               alarm got triggered = 42,338 out of 49,211 (expected)
               => 86% success rate
            */
            // Test alarmManager.set() with battery optimization off (RTC, charging off, screen off, and app not killed)
            /* Test results:
               In a span of 3 hours, and set to be running every 5 seconds, number of times
               alarm got triggered = 67 out of 2,180 (expected)
               => 3% success rate
               Even when charging but screen off, the alarm will skip
            */
//             alarmManager.set(AlarmManager.RTC, nowTime + 5000, pendingIntent)

            // setRepeating(): Cannot work due to its 60 seconds minimum interval limit
            // alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 5000, 5000, pendingIntent)

            // setAlarmClock(): Same observation as set(), but wake screen seems mandatory (update: NOT)
            // Test alarmManager.setAlarmClock() with battery optimization off (charging off, screen off, and app not killed)
            /* Test results:
               In a span of 23 minutes, and set to be running every 5 seconds, number of times
               alarm got triggered = 246 out of 278 (expected)
               => 88% success rate
            */
            var newTime = System.currentTimeMillis() + 5000
            if (prevTime + 5000 < System.currentTimeMillis() - 1000) {
                newTime = prevTime + 5000
            }
            val alarmClockInfo = AlarmManager.AlarmClockInfo(newTime, pendingIntent)
            if (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmManager.canScheduleExactAlarms()
                } else {
                    true
                }
            ) {
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        viewBinding.buttonCapture.setOnClickListener { takePhoto() }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Assumptions: Screen is off, battery optimization is off, launch is not managed automatically, no heavy load

        // Test foreground service (charging ON, power saving ON)
        /* Test results:
           Queued: 239/240 (99.58%)
        */

        // Test foreground service (charging ON, power saving OFF)
        /* Test results:
           Queued: 238/240 (99.17%)
        */

        // Test foreground service (charging OFF, power saving ON)
        /* Test results:
           Queued: 10/240 (4.17%)
        */

        // Test foreground service (charging OFF, power saving OFF)
        /* Test results:
           Queued: 29/240 (12.08%)
        */

        Log.d(TAG, foregroundServiceRunning().toString())

        if (!foregroundServiceRunning()) {
            Log.d(TAG, "Running foreground service now")
            val serviceIntent = Intent(this, ForegroundService::class.java)
            startForegroundService(serviceIntent)
        }

        // Test foreground service
        /* Test results:
           In a span of 10 hours, and set to be running every 10 seconds, number of times
           service got triggered = 1,166 out of 3,600 (expected)
           => 32% success rate
           Observed that almost stagnant overnight
        */
        /*if (!foregroundServiceRunning()) {
            Log.d(TAG, "Running service now")
            val serviceIntent = Intent(this, ForegroundService::class.java)
            startForegroundService(serviceIntent)
        }*/

        // Test foreground service with battery optimization off
        /* Test results:
           In a span of 3 hours, and set to be running every 10 seconds, number of times
           service got triggered = 550 out of 1,080 (expected)
           => 51% success rate
           In a span of 4 days, 2 hours and 12 minutes, and set to be running every 10 seconds, number of times
           service got triggered = 19,389 out of 35,353 (expected)
           => 55% success rate
        */

        // Temporarily use foreground service to capture long timelapse, but need to keep device charging
        /*if (!foregroundServiceRunning()) {
            Log.d(TAG, "Running service now")
            val serviceIntent = Intent(this, ForegroundService::class.java)
            startForegroundService(serviceIntent)
        }*/

        // Test AlarmManager
//        setAlarm()

        /* Compromises to be made if AlarmManager is to be used:
           1. App cannot be killed as repeating alarms cannot be set (using setAlarmClock())
           2. Only setAlarmClock() can be used to ensure timely deliveries
           3. Power Saving mode (in Settings > Battery) cannot be enabled as app will be killed
        */
    }

    // Test foreground service
    private fun foregroundServiceRunning(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in activityManager.getRunningServices(Int.MAX_VALUE)) {
            if (ForegroundService::class.java.name.equals(service.service.className)) {
                return true
            }
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder().build()
                .also { it.setSurfaceProvider(viewBinding.previewViewCamera.surfaceProvider) }

            imageCapture = ImageCapture.Builder().build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("SimpleDateFormat", "BatteryLife")
    public fun takePhoto() {
        /*<!--Google is against apps allowing users to whitelist battery optimizations from within the app
        using the following permission REQUEST_IGNORE_BATTERY_OPTIMIZATIONS. Instead, direct the users to
        the "Battery optimization" page.-->*/
//        startActivity(
//            Intent(
//                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
//                Uri.parse("package:$packageName")
//            )
//        )

        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

        // Get a stable reference of the modifiable image capture use case
        // This will be null if photo button is clicked before image capture is set up
        val imageCapture = imageCapture ?: return

        // Create file name and MediaStore entry
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss")
        val dateStr = dateFormat.format(Date())
        val name = "IMG_$dateStr"
        val subfolderName = "test"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Timelapse/$subfolderName")
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
                    Log.d(TAG, msg)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                }
            })
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }
}