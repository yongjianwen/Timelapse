package xiong.jianwen.timelapse.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import androidx.core.app.NotificationCompat
import xiong.jianwen.timelapse.R

class NotificationService {

    companion object {
        private const val testChannelId = "Test"

        fun buildTestNotification(
            context: Context,
            title: String,
            shortMsg: String,
            msg: String
        ): NotificationCompat.Builder {
            val notificationChannel =
                NotificationChannel(
                    testChannelId,
                    testChannelId,
                    NotificationManager.IMPORTANCE_DEFAULT
                )

            context.applicationContext.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(notificationChannel)

            val builder = NotificationCompat.Builder(context, testChannelId)

            return builder.setContentTitle(title).setContentText(shortMsg)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
        }

        fun triggerTestNotification(
            builder: NotificationCompat.Builder,
            context: Context,
            title: String,
            shortMsg: String,
            msg: String
        ) {
            builder.setContentTitle(title).setContentText(shortMsg)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setStyle(NotificationCompat.BigTextStyle().bigText(msg))

            val notificationManager =
                context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(1001, builder.build())
        }
    }
}