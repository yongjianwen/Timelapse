package xiong.jianwen.timelapse

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.ScaleAnimation
import android.widget.TextView
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
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import xiong.jianwen.timelapse.databinding.ActivityMainBinding
import xiong.jianwen.timelapse.services.ForegroundService
import xiong.jianwen.timelapse.utils.Constants
import xiong.jianwen.timelapse.utils.Utilities
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
    // interval (5 seconds ~ 1 hour = 3600 seconds) - 5s, 10s, 15s, 20s, 25s, 30s, 1m, 2m, 5m, 10m, 15m, 20m, 25m, 30m, 45m, 60m
    // duration (and resulting) - based on 600 shots
    // size estimation
    // presets (notify sunrise/sunset timings)
    // extend manually (and allow modify interval halfway)
    // preview past captures
    // gen video
    // toggle preview
    // toggle sound
    // subfolder/image file prefix naming
    // change camera selection
    // blur view - not to be implemented for now
    // pause
    // watermark
    // AE/AF lock (and reminder)

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
//                startCamera()
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

        initViews()

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Using foreground service to achieve repeating and timely triggers
        if (!foregroundServiceRunning()) {
            val serviceIntent = Intent(this, ForegroundService::class.java)
            serviceIntent.putExtra("interval", 123)
            // startForegroundService(serviceIntent)
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
            if (viewBinding.cardViewSettings.isVisible) {
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
                viewBinding.cardViewSettings.startAnimation(animationSet)
                viewBinding.cardViewSettings.visibility = View.GONE
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
                viewBinding.cardViewSettings.startAnimation(animationSet)
                viewBinding.cardViewSettings.visibility = View.VISIBLE
            }
        }


        /*viewBinding.seekBarInterval.setOnSeekBarChangeListener(object :
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
        })*/

        viewBinding.sliderInterval.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                // viewBinding.sliderInterval.stepSize = 10f
            }

            override fun onStopTrackingTouch(slider: Slider) {

            }
        })

        viewBinding.textViewIntervalValue.setOnClickListener { view ->
            viewBinding.sliderInterval.stepSize = 0f
            viewBinding.sliderInterval.value = 68.754f
        }

        /*viewBinding.seekBarDuration.setOnSeekBarChangeListener(object :
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
        })*/

        viewBinding.switchViewfinder.setOnCheckedChangeListener { _, isChecked ->
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

        viewBinding.switchShutterSound.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { userPreferences.saveIsMuted(!isChecked) }
        }

        viewBinding.sliderWidthControl.addOnChangeListener { _, value, _ ->
            val p = viewBinding.cardViewSettings.layoutParams
            p.width = Utilities.dpToPx(value.toInt())
            viewBinding.cardViewSettings.requestLayout()
        }
    }

    private fun initViews() {
        val sliderTickDiameter = resources.getDimension(R.dimen.slider_tick_diameter).toInt()
        val sliderLineWidth = resources.getDimension(R.dimen.slider_line_width).toInt()
        val sliderLineHeight = resources.getDimension(R.dimen.slider_line_height).toInt()
        val sliderMajorLineHeight = resources.getDimension(R.dimen.slider_major_line_height).toInt()
        val sliderMarkingWidth = resources.getDimension(R.dimen.slider_marking_width).toInt()

        // Initialize interval slider
        // TODO: Change to read from DataStore
        viewBinding.sliderInterval.stepSize =
            resources.getInteger(R.integer.intervalStartInSeconds).toFloat()
        viewBinding.sliderInterval.valueFrom =
            resources.getInteger(R.integer.intervalStartInSeconds).toFloat()
        viewBinding.sliderInterval.valueTo = resources.getInteger(R.integer.intervalStartInSeconds)
            .toFloat() * Constants.INTERVAL_MAP.size

        // Initialize duration slider
        updateDurationSliderRange(Utilities.mapIntervalInSeconds(viewBinding.sliderInterval.value))

        // Update layout margins
        for (view in arrayOf(
            viewBinding.flexboxLayoutIntervalTick,
            viewBinding.flexboxLayoutIntervalLine,
            viewBinding.flexboxLayoutIntervalMarking,
            viewBinding.flexboxLayoutDurationTick,
        )) {
            val p = view.layoutParams as MarginLayoutParams
            val itemHalfWidth = when (view) {
                viewBinding.flexboxLayoutIntervalTick -> sliderTickDiameter / 2f
                viewBinding.flexboxLayoutIntervalLine -> sliderLineWidth / 2f
                viewBinding.flexboxLayoutIntervalMarking -> sliderMarkingWidth / 2f
                viewBinding.flexboxLayoutDurationTick -> sliderTickDiameter / 2f
                else -> 0f
            }
            // Formula to calculate side margins: trackHeight / 2 - itemHalfWidth - 2dp
            val margin =
                (viewBinding.sliderInterval.trackHeight / 2f - itemHalfWidth - Utilities.dpToPx(2)).toInt()
            p.setMargins(margin, 0, margin, 0)
            view.requestLayout()
        }

        // Populate ticks, lines and markings for interval slider
        for (entry in Constants.INTERVAL_MAP.entries) {
            val (key, isMajor) = entry
            val tick =
                View(
                    this,
                    null,
                    if (isMajor) R.attr.intervalTickMajorStyle else R.attr.intervalTickStyle
                )
            val tickLayoutParams =
                FlexboxLayout.LayoutParams(sliderTickDiameter, sliderTickDiameter)

            val line = View(
                this,
                null,
                if (isMajor) R.attr.intervalLineMajorStyle else R.attr.intervalLineStyle
            )
            val lineLayoutParams = FlexboxLayout.LayoutParams(
                sliderLineWidth,
                if (isMajor) sliderMajorLineHeight else sliderLineHeight
            )
            if (isMajor) lineLayoutParams.setMargins(0, sliderTickDiameter / 2, 0, 0)

            val marking = TextView(
                this,
                null,
                if (isMajor) R.attr.intervalMarkingMajorStyle else R.attr.intervalMarkingStyle
            )
            val markingLayoutParams = FlexboxLayout.LayoutParams(
                sliderMarkingWidth,
                FlexboxLayout.LayoutParams.WRAP_CONTENT
            )
            if (!isMajor) markingLayoutParams.setMargins(0, -sliderTickDiameter, 0, 0)
            marking.text = Utilities.formatDuration(key, false)

            // viewBinding.flexboxLayoutIntervalTick.addView(view)
            // The above will not work if width and height are not set again as below
            viewBinding.flexboxLayoutIntervalTick.addView(tick, tickLayoutParams)
            viewBinding.flexboxLayoutIntervalLine.addView(line, lineLayoutParams)
            viewBinding.flexboxLayoutIntervalMarking.addView(marking, markingLayoutParams)
        }

        // Populate ticks for duration slider
        for (count in 1..resources.getInteger(R.integer.numOfDurationGaps)) {
            val tick = View(this, null, R.attr.intervalTickStyle)
            val tickLayoutParams =
                FlexboxLayout.LayoutParams(sliderTickDiameter, sliderTickDiameter)
            viewBinding.flexboxLayoutDurationTick.addView(tick, tickLayoutParams)
        }

        viewBinding.buttonCapture.setOnClickListener { takePhoto() }

        viewBinding.sliderInterval.addOnChangeListener { _, value, _ ->
            val mappedInterval = Utilities.mapIntervalInSeconds(value)
            viewBinding.textViewIntervalValue.text = Utilities.formatDuration(mappedInterval)

            updateDurationSliderRange(mappedInterval)

            val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }

        viewBinding.sliderDuration.addOnChangeListener { _, value, _ ->
            viewBinding.textViewDurationValue.text = getString(
                R.string.duration_display_value,
                Utilities.formatDuration(value.toInt()),
                getNumOfShots().toString()
            )

            val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }
    }

    private fun updateDurationSliderRange(intervalInSeconds: Int) {
        val newDurationValueTo =
            (intervalInSeconds * resources.getInteger(R.integer.numOfShotsAsMaxDuration)).toFloat()
        val newDurationStepSize =
            newDurationValueTo / resources.getInteger(R.integer.numOfDurationGaps)

        viewBinding.sliderDuration.stepSize = newDurationStepSize
        viewBinding.sliderDuration.valueFrom = newDurationStepSize
        viewBinding.sliderDuration.valueTo = newDurationValueTo
        viewBinding.sliderDuration.value =
            ((viewBinding.sliderDuration.value / newDurationStepSize).toInt() * newDurationStepSize).coerceIn(
                newDurationStepSize,
                newDurationValueTo
            )

        viewBinding.textViewDurationValue.text = getString(
            R.string.duration_display_value,
            Utilities.formatDuration(viewBinding.sliderDuration.value.toInt()),
            getNumOfShots().toString()
        )
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")

        userPreferences = UserPreferences(this)
        /*lifecycleScope.launch {
            userPreferences.saveUserPreferences(
                Constants.DEFAULT_INTERVAL,
                Constants.DEFAULT_DURATION,
                Constants.DEFAULT_IS_MUTED
            )
        }*/

        MaterialAlertDialogBuilder(
            this, R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog_FullWidthButtons
        ).setTitle("Turn Off Battery Optimization")
            .setMessage("Timelapse requires to continue running the app when in background. Turn off battery optimization in Settings.")
//            .setNegativeButton("Cancel") { dialog, which ->
//                // Respond to negative button press
//            }
            .setPositiveButton("OK") { dialog, which ->
                // startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            .setCancelable(false)
//            .show()
    }

    private fun getNumOfShots(): Int {
        val intervalValue = Utilities.mapIntervalInSeconds(viewBinding.sliderInterval.value)
        val durationValue = viewBinding.sliderDuration.value
        return (durationValue / intervalValue).toInt()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d(TAG, "onWindowFocusChanged: $hasFocus")
        // Closing the app: onWindowFocusChanged: false -> onPause
        // Opening the app: onResume -> onWindowFocusChanged: true

        // Opening side panel: onWindowFocusChanged: false
        // Using another app from side panel: no change
        // Touching back on the app: onWindowFocusChanged: true
        // Touching on another app: onWindowFocusChanged: false
        // Minimizing another app: onWindowFocusChanged: true
        // Restoring another app: onWindowFocusChanged: false

        // Touching another app in split screen: onWindowFocusChanged: false
        // Touching back on the app in split screen: onWindowFocusChanged: true

        // Dragging down notification center/control panel: onWindowFocusChanged: false
        // Dragging up notification center/control panel: onWindowFocusChanged: true

        if (hasFocus) {
            /* Google is against apps allowing users to whitelist battery optimizations from within
               the app using the permission REQUEST_IGNORE_BATTERY_OPTIMIZATIONS and the following
               intent. Instead, direct the users to the "Battery optimization" page for them to
               manually change the settings.
            */
            /*startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )*/

            MaterialAlertDialogBuilder(
                this, R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog_FullWidthButtons
                //,R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog_FullWidthButtons
            ).setTitle("Title").setMessage("Message")
                .setNegativeButton("Decline") { dialog, which ->
                    // Respond to negative button press
                }
                .setPositiveButton("Accept") { dialog, which ->
                    // startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
//                .show()
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
            // -1: CameraSelector.LENS_FACING_UNKNOWN
            // 0: CameraSelector.LENS_FACING_FRONT
            // 1: CameraSelector.LENS_FACING_BACK
            // 2: CameraSelector.LENS_FACING_EXTERNAL
            var arr = listOf<Int>()
            for (cameraInfo in cameraProvider.availableCameraInfos) {
                var cameraName: String = when (cameraInfo.lensFacing) {
                    CameraSelector.LENS_FACING_FRONT -> "Front"
                    CameraSelector.LENS_FACING_BACK -> "Back"
                    else -> "Other"
                }
                Log.d(TAG, cameraInfo.lensFacing.toString() + ": " + cameraName)
                // Must use MaterialButton (it seems that using Button will not work)
                val newButton = MaterialButton(
                    viewBinding.buttonToggleGroupCamera.context,
                    null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle
                )
                newButton.id = View.generateViewId()
                arr = arr.plus(newButton.id)
                newButton.text = cameraName
                newButton.minHeight = 0
                newButton.minWidth = 0
                // viewBinding.buttonToggleGroupCamera.addView(newButton)
                /*viewBinding.buttonToggleGroupCamera.post {
                    viewBinding.buttonToggleGroupCamera.addView(
                        newButton, ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                    viewBinding.buttonToggleGroupCamera.invalidate()
                }*/
                viewBinding.buttonToggleGroupCamera.addView(
                    newButton, ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
//                viewBinding.buttonToggleGroupCamera.invalidate()
                // Log.d(TAG, "new button added: " + viewBinding.buttonToggleGroupCamera.childCount)

                newButton.setOnClickListener { view ->
                    val selectedCamera = arr.indexOf(view.id)
                    Toast.makeText(this, selectedCamera.toString(), Toast.LENGTH_SHORT).show()
                }
            }
            viewBinding.buttonToggleGroupCamera.check(arr.first())

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // switch
                viewBinding.switchViewfinder.isChecked = false

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )

                // switch
                viewBinding.switchViewfinder.isChecked = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("SimpleDateFormat", "BatteryLife")
    public fun takePhoto() {
        // Using foreground service to achieve repeating and timely triggers
        if (!foregroundServiceRunning()) {
            val serviceIntent = Intent(this, ForegroundService::class.java)
            // serviceIntent.putExtra("interval", 5)
            startForegroundService(serviceIntent)
        }

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
        ContextCompat.checkSelfPermission(
            baseContext,
            it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }
}