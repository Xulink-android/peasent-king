package fr.local.peasantassistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.SystemClock

/** Thin Android gesture adapter. Callers must supply a freshly observed, uniquely resolved target. */
class AccessibilityGestureDispatcher(private val service: AccessibilityService) {
    fun dispatch(
        target: ObservedGameTargetResolver.Gesture,
        userEnabled: Boolean,
        gameForeground: Boolean,
        screenConfirmed: Boolean,
        targetConfirmed: Boolean,
        observedAtMs: Long,
        width: Int,
        height: Int,
        callback: (completed: Boolean) -> Unit
    ): Boolean {
        val request = GestureSafetyGate.Request(
            userEnabled = userEnabled,
            gameForeground = gameForeground,
            screenConfirmed = screenConfirmed,
            targetConfirmed = targetConfirmed,
            observationAgeMs = SystemClock.elapsedRealtime() - observedAtMs,
            screenWidth = width,
            screenHeight = height,
            x = target.start.x,
            y = target.start.y
        )
        if (!GestureSafetyGate.allowedPath(request, target.end.x, target.end.y)) return false
        val path = Path().apply {
            moveTo(target.start.x.toFloat(), target.start.y.toFloat())
            if (target.isDrag) lineTo(target.end.x.toFloat(), target.end.y.toFloat())
        }
        val duration = if (target.isDrag) 650L else 90L
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        return service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = callback(true)
            override fun onCancelled(gestureDescription: GestureDescription?) = callback(false)
        }, null)
    }
}
