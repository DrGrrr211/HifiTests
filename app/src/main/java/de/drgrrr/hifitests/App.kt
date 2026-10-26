package de.drgrrr.hifitests

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

const val CHANNEL = "neue_tests"

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel(CHANNEL, "Neue Testberichte", NotificationManager.IMPORTANCE_DEFAULT)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        val req = PeriodicWorkRequestBuilder<NewTestsWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("neue_tests", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

/** Prüft zweimal täglich auf neue Tests und meldet sie. */
class NewTestsWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("seen", Context.MODE_PRIVATE)
        val file = try { Repo(applicationContext).refresh() } catch (e: Exception) { return Result.retry() }
        val ids = file.reviews.map { it.id }.toSet()
        val seen = prefs.getStringSet("ids", null)
        prefs.edit().putStringSet("ids", ids).apply()
        if (seen == null) return Result.success() // erster Lauf: nur merken
        val fresh = file.reviews.filter { it.id !in seen }
        if (fresh.isNotEmpty()) notify(applicationContext, fresh)
        return Result.success()
    }

    private fun notify(ctx: Context, fresh: List<Review>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = NotificationCompat.InboxStyle()
        fresh.take(6).forEach { style.addLine("${it.source}: ${it.product.ifBlank { it.title }}") }
        if (fresh.size > 6) style.setSummaryText("+${fresh.size - 6} weitere")
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(if (fresh.size == 1) "1 neuer HiFi-Test" else "${fresh.size} neue HiFi-Tests")
            .setContentText(fresh.first().let { "${it.source}: ${it.product.ifBlank { it.title }}" })
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(1, n)
    }
}
