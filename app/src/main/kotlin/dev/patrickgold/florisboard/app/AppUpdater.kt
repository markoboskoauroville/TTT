package dev.patrickgold.florisboard.app

// v48 (Marko, 14.11.2026: "When the application starts, it checks for it, downloads it, and asks you, 'Do you want to
// update?' Then I say 'yes,' and it downloads and runs the update."). The same shape as the Mac kit
// (MANTRA_MANIFEST kits/mac-updater, modules/update-at-start.md):
//   start → GitHub's Latest release of this public repo → tag vN with N > versionCode → the APK is downloaded into the
//   app's cache (on Wi-Fi first, then the question; on mobile data the question first, with its size: SIM data) →
//   "Mantra Remote Viewing vN is ready · Do you want to update?" UPDATE / LATER → UPDATE installs it through Android's
//   PackageInstaller (the same key every build, updatable.md, so it goes over the old one and keeps the settings).
// After the update the app says once "Updated to vN" (from vM), so he knows the new one is running.

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object AppUpdater {
    private const val REPO = "markoboskoauroville/TTT"   // the app's PUBLIC repo with APK releases
    private const val ACTION = "com.mantraproductions.tttlight.UPDATE_STATUS"
    private const val TAG = "AppUpdater"
    private const val APP = "TTT"   // the app's name as he sees it
    private const val OFFSET = 1000L
    private fun shown(v: Long) = if (v > OFFSET) v - OFFSET else v
    @Volatile private var busy = false
    private var pending: File? = null   // v50: waiting for "Allow from this source"

    /** v50: back from the one-time "Allow from this source" page, the install carries on by itself. */
    fun resume(a: Activity) {
        val p = pending ?: return
        if (a.packageManager.canRequestPackageInstalls()) { pending = null; install(a, p) }
    }

    private fun current(c: Context): Long =
        runCatching { c.packageManager.getPackageInfo(c.packageName, 0).longVersionCode }.getOrDefault(0L)

    /** Called once from MainActivity.onCreate. */
    fun checkAtStart(a: Activity) {
        sayIfUpdated(a)
        if (busy) return
        busy = true
        thread(name = "updater") {
            try { check(a) } catch (e: Exception) { Log.w(TAG, "check failed: $e") } finally { busy = false }
        }
    }

    /** "Updated to vN": once, on the first start of a new version. */
    private fun sayIfUpdated(a: Activity) {
        val p = a.getSharedPreferences("updater", Context.MODE_PRIVATE)
        val now = current(a)
        val last = p.getLong("lastRun", 0L)
        if (last != 0L && last < now) {
            AlertDialog.Builder(a).setTitle("Updated to build ${shown(now)}")
                .setMessage("$APP went from build ${shown(last)} to build ${shown(now)}. Everything you set is still here.")
                .setPositiveButton("OK", null).show()
        }
        p.edit().putLong("lastRun", now).apply()
        File(a.cacheDir, "update").listFiles()?.forEach {      // the installed version and older: no longer needed
            val n = Regex("v(\\d+)").find(it.name)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            if (n <= now) it.delete()
        }
    }

    private fun check(a: Activity) {
        val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        if (c.responseCode != 200) { Log.i(TAG, "no release info (${c.responseCode})"); return }
        val j = JSONObject(c.inputStream.bufferedReader().readText())
        val tag = j.optString("tag_name")
        val v = (tag.filter { it.isDigit() }.toLongOrNull() ?: return) + OFFSET   // tag build-N, versionCode 1000+N
        val now = current(a)
        if (v <= now) { Log.i(TAG, "build ${shown(now)} is the newest (Latest is $tag)"); return }
        val assets = j.optJSONArray("assets") ?: return
        var url = ""; var size = 0L; var name = ""
        for (i in 0 until assets.length()) {
            val o = assets.getJSONObject(i)
            if (o.optString("name").endsWith(".apk")) { url = o.optString("browser_download_url"); size = o.optLong("size"); name = o.optString("name") }
        }
        if (url.isEmpty()) return
        val notes = j.optString("body").trim().take(300)
        val dir = File(a.cacheDir, "update").apply { mkdirs() }
        val apk = File(dir, "v$v.apk")
        val have = apk.exists() && apk.length() == size
        val mb = "%.0f MB".format(size / 1e6)
        if (have || !metered(a)) {
            if (!have && !download(url, apk, size)) return
            a.runOnUiThread { ask(a, v, now, notes, "", apk, null, 0) }
        } else {
            a.runOnUiThread { ask(a, v, now, notes, "\n\nOn mobile data: the download is $mb.", apk, url, size) }
        }
    }

    private fun metered(c: Context): Boolean {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        val nc = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
        return !nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun download(url: String, to: File, size: Long): Boolean = runCatching {
        val part = File(to.path + ".part")
        (URL(url).openConnection() as HttpURLConnection).run {
            instanceFollowRedirects = true; connectTimeout = 10000; readTimeout = 30000
            inputStream.use { i -> part.outputStream().use { o -> i.copyTo(o, 64 * 1024) } }
        }
        if (part.length() != size) { part.delete(); false } else part.renameTo(to)
    }.getOrElse { Log.w(TAG, "download failed: $it"); false }

    private fun ask(a: Activity, v: Long, now: Long, notes: String, extra: String, apk: File, url: String?, size: Long) {
        if (a.isFinishing) return
        AlertDialog.Builder(a)
            .setTitle("$APP build ${shown(v)} is ready")
            .setMessage("You have build ${shown(now)}. Do you want to update?" + (if (notes.isEmpty()) "" else "\n\nWhat is new: $notes") + extra)
            .setPositiveButton("UPDATE") { _, _ ->
                if (url == null) install(a, apk)
                else {
                    Toast.makeText(a, "Downloading build ${shown(v)}…", Toast.LENGTH_SHORT).show()
                    thread { if (download(url, apk, size)) a.runOnUiThread { install(a, apk) }
                             else a.runOnUiThread { Toast.makeText(a, "Download failed. Asked again at the next start.", Toast.LENGTH_LONG).show() } }
                }
            }
            .setNegativeButton("LATER", null)
            .setCancelable(false)
            .show()
    }

    private fun install(a: Activity, apk: File) {
        // Android asks once to allow installs from this app; after that only its own confirmation
        if (!a.packageManager.canRequestPackageInstalls()) {
            pending = apk
            Toast.makeText(a, "Allow $APP to install updates, then come back.", Toast.LENGTH_LONG).show()
            a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}")))
            return
        }
        val pi = a.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(a.packageName)
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            s.openWrite("app.apk", 0, apk.length()).use { o -> apk.inputStream().use { it.copyTo(o) }; s.fsync(o) }
            val app = a.applicationContext
            app.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    when (val st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            @Suppress("DEPRECATION") val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); if (confirm != null) c.startActivity(confirm)
                        }
                        PackageInstaller.STATUS_SUCCESS -> Log.i(TAG, "installed")
                        else -> {
                            Log.w(TAG, "install status $st ${i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
                            Toast.makeText(c, "Update failed: ${i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}", Toast.LENGTH_LONG).show()
                            runCatching { c.unregisterReceiver(this) }
                        }
                    }
                }
            }, IntentFilter(ACTION), Context.RECEIVER_NOT_EXPORTED)
            val sender = PendingIntent.getBroadcast(a, id, Intent(ACTION).setPackage(a.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            s.commit(sender.intentSender)
        }
    }
}
