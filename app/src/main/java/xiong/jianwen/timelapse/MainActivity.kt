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
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.ScaleAnimation
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import xiong.jianwen.timelapse.databinding.ActivityMainBinding
import xiong.jianwen.timelapse.services.ForegroundService
import xiong.jianwen.timelapse.utils.Constants
import xiong.jianwen.timelapse.utils.UserPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), CoroutineScope {

    private var job: Job = Job()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    // align to minute
    // interval
    // duration
    // presets
    // extend manually
    // preview past captures
    // gen video
    // toggle preview
    // toggle sound
    // subfolder/image file prefix naming
    // change camera selection
    // blur view - not to be implemented for now

    init {
        instance = this
    }

    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraProvider: ProcessCameraProvider

    private lateinit var userPreferences: UserPreferences

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
            serviceIntent.putExtra("interval", 123)
            startForegroundService(serviceIntent)
        }

        /* Compromises to be made if AlarmManager is to be used:
           1. App cannot be killed as repeating alarms cannot be set (e.g. using setAlarmClock())
           2. Only setAlarmClock() can be used to ensure timely deliveries
           3. Power Saving mode (in Settings > Battery) cannot be enabled as app will be killed
        */

        viewBinding.buttonSettings.setOnClickListener {
            // Height animation
            /*val anim: ValueAnimator// = ValueAnimator.ofInt(originalHeight, 0)
            if (viewBinding.layoutSettings.measuredHeight == 0) {
                anim = ValueAnimator.ofInt(0, 875)
            } else {
                anim = ValueAnimator.ofInt(875, 0)
            }
            anim.addUpdateListener { valueAnimator ->
                val value = valueAnimator.animatedValue as Int
                val layoutParams: ViewGroup.LayoutParams = viewBinding.layoutSettings.layoutParams
                layoutParams.height = value
                viewBinding.layoutSettings.layoutParams = layoutParams
            }
            anim.duration = 1000
            anim.interpolator = DecelerateInterpolator()
            anim.start()*/

            /*val pvhX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.5f)
            val pvhY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.9f)
            val scaleAnimation: ObjectAnimator =
                ObjectAnimator.ofPropertyValuesHolder(viewBinding.blurView, pvhX, pvhY)

            val setAnimation = AnimatorSet()
            setAnimation.play(scaleAnimation)
            setAnimation.start()*/

            // Visibility
            /*if (viewBinding.layoutSettings.isVisible) viewBinding.layoutSettings.visibility = View.GONE
            else viewBinding.layoutSettings.visibility = View.VISIBLE*/

            // Animation
            if (viewBinding.layoutSettings.isVisible) {
                val scaleAnimation = ScaleAnimation(
                    1f, 0.1f,   // Start and end values for the X axis scaling
                    1f, 0.1f,   // Start and end values for the Y axis scaling
                    Animation.RELATIVE_TO_SELF, 0.9f,   // Pivot point of X scaling
                    Animation.RELATIVE_TO_SELF, 1.1f    // Pivot point of Y scaling
                )
                scaleAnimation.fillAfter = true // Needed to keep the result of the animation
                scaleAnimation.interpolator = DecelerateInterpolator()
                scaleAnimation.duration = 350

                val alphaAnimation = AlphaAnimation(1f, 0f)
                alphaAnimation.fillAfter = true
                alphaAnimation.duration = 350

                val animationSet = AnimationSet(true)
                animationSet.addAnimation(scaleAnimation)
                animationSet.addAnimation(alphaAnimation)
                viewBinding.layoutSettings.startAnimation(animationSet)
                viewBinding.layoutSettings.visibility = View.GONE
            } else {
                val scaleAnimation = ScaleAnimation(
                    0f, 1f,   // Start and end values for the X axis scaling
                    0f, 1f,   // Start and end values for the Y axis scaling
                    Animation.RELATIVE_TO_SELF, 0.9f,   // Pivot point of X scaling
                    Animation.RELATIVE_TO_SELF, 1.1f    // Pivot point of Y scaling
                )
                scaleAnimation.fillAfter = true // Needed to keep the result of the animation
                scaleAnimation.interpolator = DecelerateInterpolator()
                scaleAnimation.duration = 350

                val alphaAnimation = AlphaAnimation(0f, 1f)
                alphaAnimation.fillAfter = true
                alphaAnimation.duration = 350

                val animationSet = AnimationSet(true)
                animationSet.addAnimation(scaleAnimation)
                animationSet.addAnimation(alphaAnimation)
                viewBinding.layoutSettings.startAnimation(animationSet)
                viewBinding.layoutSettings.visibility = View.VISIBLE
            }
        }

        viewBinding.seekBarInterval.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                viewBinding.textViewInterval.text = p1.toString()
                lifecycleScope.launch {
                    var duration = 0
                    lifecycleScope.launch {
                        userPreferences.durationFlow.collect {
                            duration = it
                        }
                    }
                    var isMuted = true
                    lifecycleScope.launch {
                        userPreferences.isMutedFlow.collect {
                            isMuted = it
                        }
                    }
                    userPreferences.saveUserPreferences(p1, duration, isMuted)
                }
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}

            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        viewBinding.seekBarDuration.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                viewBinding.textViewDuration.text = p1.toString()
                lifecycleScope.launch {
                    var interval = 0
                    lifecycleScope.launch {
                        userPreferences.intervalFlow.collect {
                            interval = it
                        }
                    }
                    var isMuted = true
                    lifecycleScope.launch {
                        userPreferences.isMutedFlow.collect {
                            isMuted = it
                        }
                    }
                    userPreferences.saveUserPreferences(interval, p1, isMuted)
                }
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}

            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        viewBinding.switchPreview.setOnCheckedChangeListener { _, isChecked ->
            /*Toast.makeText(
                applicationContext,
                isChecked.toString(),
                Toast.LENGTH_SHORT
            ).show()*/

            if (isChecked) {
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    Preview.Builder().build()
                        .also { it.setSurfaceProvider(viewBinding.previewViewCamera.surfaceProvider) },
                    imageCapture
                )
            } else {
                cameraProvider.unbindAll()
            }
        }

        viewBinding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { userPreferences.saveIsMuted(!isChecked) }
        }
    }

    override fun onResume() {
        super.onResume()
        userPreferences = UserPreferences(this)
        lifecycleScope.launch {
            userPreferences.saveUserPreferences(
                Constants.DEFAULT_INTERVAL,
                Constants.DEFAULT_DURATION,
                Constants.DEFAULT_IS_MUTED
            )
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
            // val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder().build()
                .also { it.setSurfaceProvider(viewBinding.previewViewCamera.surfaceProvider) }

            imageCapture = ImageCapture.Builder().build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // switch
                viewBinding.switchPreview.isChecked = false

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)

                // switch
                viewBinding.switchPreview.isChecked = true
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

        // var age by rememberSaveable { mutableStateOf("") }
//        Toast.makeText(this, userInfo.userAgeFlow.collectAsState(0).value.toString(), Toast.LENGTH_LONG).show()
//        return

        /*var age = 0
        var myName = ""
        lifecycleScope.launch {
            userPreferences.userAgeFlow.collect {
                age = it
            }
        }
        lifecycleScope.launch {
            userPreferences.userNameFlow.collect {
                myName = it
            }
        }*/
//        Toast.makeText(applicationContext, "$age, $myName", Toast.LENGTH_LONG).show()
        return

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

    fun showToast(toast: String?) {
        runOnUiThread {
            Toast.makeText(applicationContext(), toast, Toast.LENGTH_SHORT).show()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }
}