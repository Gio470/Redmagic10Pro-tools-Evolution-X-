package com.elitedarkkaiser.redmagic

import android.content.Context
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.elitedarkkaiser.redmagic.state.LedState
import java.util.concurrent.atomic.AtomicInteger

/** Gives an open profile picker temporary LED ownership, then resumes the saved owner. */
internal object LedPreview {
    private val sessions = AtomicInteger()
    val isActive: Boolean get() = sessions.get() > 0

    /** Embedded card contents preview only while their page is attached, and can reopen. */
    fun bind(view: View, enabled: () -> Boolean,
        read: () -> Map<LiveLedPreview.Zone, LedState>): () -> Unit {
        var preview: LiveLedPreview? = null
        fun refresh() { read().forEach { (zone, state) -> preview?.update(zone, state) } }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                preview = session(v.context, enabled)
                refresh()
            }
            override fun onViewDetachedFromWindow(v: View) {
                preview?.close()
                preview = null
            }
        })
        return ::refresh
    }

    fun session(context: Context, enabled: () -> Boolean): LiveLedPreview {
        val owner = context as? LifecycleOwner
        var observer: DefaultLifecycleObserver? = null
        val preview = LiveLedPreview(
            enabled = enabled,
            apply = ::apply,
            acquire = { sessions.incrementAndGet() },
            restore = { if (sessions.decrementAndGet() == 0) restore(context) },
            dispose = { observer?.let { owner?.lifecycle?.removeObserver(it) } }
        )
        val lifecycleObserver = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { preview.resume() }
            override fun onStop(owner: LifecycleOwner) { preview.pause() }
            override fun onDestroy(owner: LifecycleOwner) { preview.close() }
        }
        observer = lifecycleObserver
        owner?.lifecycle?.addObserver(lifecycleObserver)
        return preview
    }

    private fun apply(zone: LiveLedPreview.Zone, state: LedState) {
        when (zone) {
            LiveLedPreview.Zone.FAN -> when {
                !state.enabled -> HardwareController.setFanLedEnabled(false)
                state.effect.startsWith("preset:") ->
                    HardwareController.setFanLedStockPreset(state.effect.removePrefix("preset:"))
                else -> HardwareController.setFanLedEffect(state.effect, state.color)
            }
            LiveLedPreview.Zone.LOGO -> if (state.enabled)
                HardwareController.setLogoLedEffect(state.effect, state.color)
            else HardwareController.setLogoLedEnabled(false)
            LiveLedPreview.Zone.SHOULDER -> if (state.enabled)
                HardwareController.setShoulderLedEffect(state.effect, state.color)
            else HardwareController.setShoulderLedEnabled(false)
        }
    }

    private fun restore(context: Context) {
        when {
            ChargingLedState.isEnabled(context) && ChargingLedState.isChargingNow(context) -> {
                ChargingLedState.setActive(context, true)
                ChargingLedState.applyChargingProfile(context)
            }
            CallLightingState.isActive(context) -> Unit
            else -> {
                if (!HardwareScreenPolicy.blockNormalLedsWhileScreenOff(context, "preview-closed")) {
                    val fan = savedFanLedStateStorage(context)
                    if (fan.color != FanLedCycle.CYCLE_COLOR) apply(LiveLedPreview.Zone.FAN, fan)
                    val logo = savedLogoLedStateStorage(context)
                    apply(LiveLedPreview.Zone.LOGO, logo)
                    if (fan.enabled || logo.enabled || savedShoulderLedStateStorage(context).enabled)
                        HardwareServiceActions.startFanLed(context)
                    else HardwareServiceActions.stopFanLed(context)
                }
            }
        }
        // A call may have arrived while the picker held the LEDs; the service remembers its state.
        if (CallLightingState.isEnabled(context)) HardwareServiceActions.startCallLighting(context)
        GameModeActions.startServiceSilentlyIfPermitted(context)
    }
}
