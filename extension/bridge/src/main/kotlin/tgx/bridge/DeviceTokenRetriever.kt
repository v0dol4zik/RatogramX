package tgx.bridge

import android.content.Context
import org.drinkless.tdlib.TdApi.DeviceToken

interface TokenRetrieverListener {
  fun onTokenRetrievalSuccess(token: DeviceToken)
  fun onTokenRetrievalError(errorKey: String, e: Throwable?)
}

abstract class DeviceTokenRetriever(
  @JvmField val name: String
) {
  companion object {
    const val ERROR_INITIALIZATION = "INITIALIZATION_ERROR"
    const val ERROR_CONFIGURATION_MISSING = "PUSH_CONFIGURATION_MISSING"
    const val ERROR_SERVICE_UNAVAILABLE = "PUSH_SERVICE_UNAVAILABLE"

    @JvmStatic fun canRetry(errorKey: String?): Boolean =
      errorKey != ERROR_CONFIGURATION_MISSING && errorKey != ERROR_SERVICE_UNAVAILABLE &&
        errorKey != "BAD_CONFIG" && errorKey != "AUTHENTICATION_FAILED" &&
        errorKey != "INVALID_SENDER" && errorKey != "MISMATCH_SENDER_ID"
  }

  private var isInitialized = false
  var initializationErrorKey: String = ERROR_INITIALIZATION
    private set
  var initializationError: Throwable? = null
    private set

  protected fun initializationFailed(errorKey: String, cause: Throwable? = null): Boolean {
    initializationErrorKey = errorKey
    initializationError = cause
    return false
  }

  fun initialize(context: Context): Boolean {
    return if (isInitialized) {
      true
    } else {
      initializationErrorKey = ERROR_INITIALIZATION
      initializationError = null
      val result = performInitialization(context)
      if (result) {
        isInitialized = true
      }
      result
    }
  }

  fun retrieveDeviceToken(context: Context, listener: TokenRetrieverListener) {
    if (!isInitialized) {
      listener.onTokenRetrievalError(initializationErrorKey, initializationError)
      return
    }
    fetchDeviceToken(context, listener)
  }

  open val configuration: String = ""
  abstract fun isAvailable(context: Context): Boolean
  protected abstract fun performInitialization(context: Context): Boolean
  protected abstract fun fetchDeviceToken(context: Context, listener: TokenRetrieverListener)
}

fun DeviceTokenRetriever?.isAvailable(context: Context) =
  this?.isAvailable(context) ?: false
