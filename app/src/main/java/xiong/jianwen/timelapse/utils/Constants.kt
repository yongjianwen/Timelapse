package xiong.jianwen.timelapse.utils

class Constants {

    companion object {
        const val NO_CAMERA = true
        const val WAKE_LOCK_TIMEOUT = 1 * 24 * 60 * 60 * 1000L  // 1 day

        // Preferences DataStore
        const val DEFAULT_INTERVAL = 5      // 5 seconds
        const val DEFAULT_DURATION = 3600   // 60 minutes
        const val DEFAULT_IS_MUTED = true
    }
}