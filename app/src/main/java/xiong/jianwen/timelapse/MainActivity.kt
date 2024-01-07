package xiong.jianwen.timelapse

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.BounceInterpolator
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation
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
import xiong.jianwen.timelapse.services.ForegroundService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {

    // align to minute
    // interval
    // duration
    // extend manually
    // preview past captures
    // gen video
    // toggle preview
    // toggle sound
    // subfolder naming
    // change camera selection
    // blur view - not to be implemented for now

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

        fun applicationContext(): Context {
            return instance!!.applicationContext
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

        // Using foreground service to achieve repeating and timely triggers
        if (!foregroundServiceRunning()) {
            val serviceIntent = Intent(this, ForegroundService::class.java)
            // startForegroundService(serviceIntent)
        }

        /* Compromises to be made if AlarmManager is to be used:
           1. App cannot be killed as repeating alarms cannot be set (e.g. using setAlarmClock())
           2. Only setAlarmClock() can be used to ensure timely deliveries
           3. Power Saving mode (in Settings > Battery) cannot be enabled as app will be killed
        */

        viewBinding.buttonSettings.setOnClickListener {
//            if (viewBinding.testView.isVisible) viewBinding.testView.visibility = View.GONE
//            else viewBinding.testView.visibility = View.VISIBLE
            val originalHeight = viewBinding.testView.measuredHeight
            /*Toast.makeText(
                applicationContext,
                "originalHeight: " + originalHeight,
                Toast.LENGTH_LONG
            ).show()*/

            // Height animation
            /*val anim: ValueAnimator// = ValueAnimator.ofInt(originalHeight, 0)
            if (viewBinding.testView.measuredHeight == 0) {
                anim = ValueAnimator.ofInt(0, 875)
            } else {
                anim = ValueAnimator.ofInt(875, 0)
            }
            anim.addUpdateListener { valueAnimator ->
                val value = valueAnimator.animatedValue as Int
                val layoutParams: ViewGroup.LayoutParams = viewBinding.testView.layoutParams
                layoutParams.height = value
                viewBinding.testView.layoutParams = layoutParams
            }
            anim.duration = 1000
            anim.interpolator = DecelerateInterpolator()
            anim.start()*/

            // Scale animation
            val anim: Animation = ScaleAnimation(
                1f, 0.1f,  // Start and end values for the X axis scaling
                1f, 0.1f,  // Start and end values for the Y axis scaling
                Animation.RELATIVE_TO_SELF, 0.7f,  // Pivot point of X scaling
                Animation.RELATIVE_TO_SELF, 0.3f
            ) // Pivot point of Y scaling

            anim.fillAfter = true // Needed to keep the result of the animation
            anim.interpolator = BounceInterpolator()
            anim.duration = 1000
//            viewBinding.blurView.startAnimation(anim)
//            viewBinding.blurView.requestLayout()
//            viewBinding.blurView.scaleX = 0.6f
//            viewBinding.blurView.scaleY = 0.6f

            val anim2 = AlphaAnimation(1f, 0f)
            anim2.fillAfter = true
            anim2.duration = 1000
//            viewBinding.testView.startAnimation(anim2)

            val anim3 = TranslateAnimation(0f, 500f, 0f, 0f)
            anim3.fillAfter = true
            anim3.duration = 1000
//            viewBinding.testView.startAnimation(anim3)

            val animationSet = AnimationSet(false)
            animationSet.addAnimation(anim)
            animationSet.addAnimation(anim2)
//            viewBinding.testView.startAnimation(animationSet)

//            viewBinding.testView.visibility = View.GONE

//            viewBinding.testView.scaleX = 0.7f
//            viewBinding.testView.scaleY = 0.7f
//            viewBinding.previewViewCamera.scaleX = 0.3f
//            viewBinding.previewViewCamera.scaleY = 0.3f
//            viewBinding.blurView.visibility = View.GONE

            /*val pvhX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.5f)
            val pvhY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.9f)
            val scaleAnimation: ObjectAnimator =
                ObjectAnimator.ofPropertyValuesHolder(viewBinding.blurView, pvhX, pvhY)

            val setAnimation = AnimatorSet()
            setAnimation.play(scaleAnimation)
            setAnimation.start()*/
        }
    }

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
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("SimpleDateFormat", "BatteryLife")
    public fun takePhoto() {
        /*Google is against apps allowing users to whitelist battery optimizations from within the app
        using the following permission REQUEST_IGNORE_BATTERY_OPTIMIZATIONS. Instead, direct the users to
        the "Battery optimization" page.*/
//        startActivity(
//            Intent(
//                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
//                Uri.parse("package:$packageName")
//            )
//        )

        // startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

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