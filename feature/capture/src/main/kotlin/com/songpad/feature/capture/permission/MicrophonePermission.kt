package com.songpad.feature.capture.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.songpad.core.model.state.PermissionState

/** §13: RECORD_AUDIO is requested on the first tap of Record, never at cold start. */
object MicrophonePermission {
    fun current(context: Context): PermissionState =
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) PermissionState.Granted
        else PermissionState.Unknown

    /** After a dialog result: denied with "don't ask again" reads as PermanentlyDenied (rationale flag false). */
    fun afterResult(context: Context, granted: Boolean): PermissionState = when {
        granted -> PermissionState.Granted
        context.findActivity()?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO) } == true -> PermissionState.NeedsRationale
        else -> PermissionState.PermanentlyDenied
    }

    fun openAppSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun Context.findActivity(): Activity? {
        var c: Context = this
        while (c is android.content.ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}

class MicrophonePermissionRequester(private val launch: () -> Unit) {
    fun request() = launch()
}

@Composable
fun rememberMicrophonePermissionRequester(onResult: (PermissionState) -> Unit): MicrophonePermissionRequester {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onResult(MicrophonePermission.afterResult(context, granted))
    }
    return remember(launcher) { MicrophonePermissionRequester { launcher.launch(Manifest.permission.RECORD_AUDIO) } }
}
