package xiong.jianwen.timelapse.utils

import android.media.MediaPlayer
import android.util.TypedValue
import xiong.jianwen.timelapse.MainActivity
import xiong.jianwen.timelapse.R
import kotlin.math.roundToInt

class Utilities {

    companion object {
        private val mp = MediaPlayer.create(MainActivity.applicationContext(), R.raw.camera)

        fun mapIntervalInSeconds(sliderValue: Float): Int {
            val context = MainActivity.applicationContext()
            val intervals = Constants.INTERVAL_MAP.keys
            val sliderStartValue = context.resources.getInteger(R.integer.intervalStartInSeconds)

            val activeTickIndex =
                (sliderValue - sliderStartValue) / sliderStartValue   // the second sliderStartValue acts as stepSize
            val matchingIndexInIntervals = activeTickIndex.toInt()

            return if (activeTickIndex == matchingIndexInIntervals.toFloat()) {
                intervals.elementAt(matchingIndexInIntervals)
            } else {
                intervals.elementAt(matchingIndexInIntervals) + ((activeTickIndex - matchingIndexInIntervals) * (intervals.elementAt(
                    matchingIndexInIntervals + 1
                ) - intervals.elementAt(matchingIndexInIntervals))).roundToInt()
            }
        }

        fun formatDuration(durationInSeconds: Int, longFormat: Boolean = true): String {
            val day = durationInSeconds / 86400
            val hour = durationInSeconds % 86400 / 3600
            val minute = durationInSeconds % 3600 / 60
            val second = durationInSeconds % 60

            var formattedDay = String.format("%02d", day) + "d"
            var formattedHour = String.format("%02d", hour) + "h"
            var formattedMinute = String.format("%02d", minute) + "m"
            var formattedSecond = String.format("%02d", second) + "s"

            if (!longFormat) {
                formattedDay = if (day == 0) "" else formattedDay
                formattedHour = if (hour == 0) "" else formattedHour
                formattedMinute = if (minute == 0) "" else formattedMinute
                formattedSecond = if (second == 0) "" else formattedSecond
            }

            return if (longFormat) {
                if (day > 0) "$formattedDay:$formattedHour:$formattedMinute:$formattedSecond"
                else if (hour > 0) "$formattedHour:$formattedMinute:$formattedSecond"
                else "$formattedMinute:$formattedSecond"
            } else {
                "$formattedDay$formattedHour$formattedMinute$formattedSecond"
            }
        }

        fun dpToPx(dp: Int): Int {
            val context = MainActivity.applicationContext()
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics
            ).toInt()
        }

        fun playSound() {
            mp.start()
        }

        fun mute() {
            mp.stop()
            mp.setVolume(0f, 0f)
        }

        fun unMute() {
            mp.reset()
            mp.setVolume(1f, 1f)
        }
    }
}