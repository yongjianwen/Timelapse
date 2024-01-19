package xiong.jianwen.timelapse.utils

class Constants {

    companion object {
        // Interval slider
        // mapOf(intervalInSeconds: isMajor)
        // 5s, 10s, 15s, 20s, 25s, 30s, 1m, 2m, 5m, 10m, 15m, 20m, 25m, 30m, 45m, 1h
        val INTERVAL_MAP = mapOf(
            5 to true,
            10 to false,
            15 to false,
            20 to false,
            25 to false,
            30 to false,
            60 to true,
            120 to false,
            300 to false,
            600 to false,
            900 to true,
            1200 to false,
            1500 to false,
            1800 to true,
            2700 to false,
            3600 to true
        )

        // Foreground service parameters
        const val INTERVAL = "interval"
        const val DURATION = "duration"

        // Foreground service
        const val NO_CAMERA = false
        const val WAKE_LOCK_TIMEOUT = 1 * 365 * 24 * 60 * 60 * 1000L  // 1 year

        // Preferences DataStore
        const val DEFAULT_INTERVAL = 5      // 5 seconds
        const val DEFAULT_DURATION = 3600   // 60 minutes
        const val DEFAULT_IS_MUTED = true
    }
}