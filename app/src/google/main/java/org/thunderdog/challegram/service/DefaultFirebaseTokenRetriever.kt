package org.thunderdog.challegram.service

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import me.vkryl.core.isEmpty
import org.thunderdog.challegram.U
import tgx.bridge.DeviceTokenRetriever
import tgx.bridge.PushManagerBridge
import java.util.regex.Pattern

abstract class DefaultFirebaseTokenRetriever : DeviceTokenRetriever("firebase") {
  override fun isAvailable(context: Context): Boolean =
    U.isGooglePlayServicesAvailable(context)

  override fun performInitialization(context: Context): Boolean {
    try {
      if (FirebaseOptions.fromResource(context) == null) {
        return initializationFailed(ERROR_CONFIGURATION_MISSING)
      }
      if (!isAvailable(context)) {
        return initializationFailed(ERROR_SERVICE_UNAVAILABLE)
      }
      PushManagerBridge.log("FirebaseApp is initializing...")
      if (FirebaseApp.initializeApp(context) != null) {
        PushManagerBridge.log("FirebaseApp initialization finished successfully")
        return true
      } else {
        PushManagerBridge.log("FirebaseApp initialization failed")
      }
    } catch (e: Throwable) {
      PushManagerBridge.error("FirebaseApp initialization failed with error", e)
      return initializationFailed(ERROR_INITIALIZATION, e)
    }
    return false
  }

  companion object {
    fun extractFirebaseErrorName(e: Throwable): String {
      val message = e.message.orEmpty()
      if (!isEmpty(message)) {
        val matcher = Pattern.compile("(?<=: )[A-Z_]+$").matcher(message)
        if (matcher.find()) {
          return matcher.group()
        }
        return message
      }
      return e.javaClass.getSimpleName()
    }
  }
}
