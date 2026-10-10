package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rive

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Loop
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.ViewModelInstance
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.CompanionVariant
import com.craftflowtechnologies.meetingmind.core.companion.CompanionVisual
import com.craftflowtechnologies.meetingmind.core.companion.CreateMode
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LevelSmoother
import com.craftflowtechnologies.meetingmind.ui.theme.CompanionPalette
import kotlinx.coroutines.android.awaitFrame
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Rive's native runtime, initialised on first use. The library's manifest removes its own
 * androidx.startup initializer, so the app must call [Rive.init]; doing it lazily here means
 * builds without `.riv` files never load the native library at all.
 */
internal object RiveRuntime {
    private val started = AtomicBoolean(false)
    @Volatile var available: Boolean = false
        private set

    fun ensureInit(context: Context): Boolean {
        if (started.compareAndSet(false, true)) {
            available = runCatching { Rive.init(context.applicationContext) }.onFailure { Log.w(Tag, "Rive init failed", it) }.isSuccess
        }
        return available
    }

    const val Tag = "Companion"
}

/**
 * The Rive renderer: one `RiveAnimationView` driving the "Companion" state machine through
 * [CompanionRiveContract]. [active] is false while off screen or not resumed, which pauses the
 * view so it does not tick. Any contract breach calls [onFailure] and the slot falls back to Canvas.
 */
@Composable
internal fun RiveCompanion(
    form: CompanionForm,
    bytes: ByteArray,
    visual: CompanionVisual,
    variant: CompanionVariant,
    palette: CompanionPalette,
    level: () -> Float,
    active: Boolean,
    onFailure: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val holder = remember(form, bytes) { RiveHolder() }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            RiveAnimationView(ctx, null).also { view ->
                holder.view = view
                val problem = runCatching {
                    if (!RiveRuntime.ensureInit(ctx)) return@runCatching "native runtime unavailable"
                    view.setRiveBytes(
                        bytes,
                        artboardName = CompanionRiveContract.artboard(form),
                        stateMachineName = CompanionRiveContract.StateMachine,
                        autoplay = true,
                        autoBind = true,
                        fit = Fit.CONTAIN,
                        alignment = Alignment.CENTER,
                        loop = Loop.AUTO
                    )
                    val sm = view.controller.stateMachines.firstOrNull() ?: return@runCatching "no state machine"
                    val missing = CompanionRiveContract.Inputs.required.filter { name -> sm.inputs.none { it.name == name } }
                    if (missing.isNotEmpty()) return@runCatching "missing inputs $missing"
                    holder.viewModel = sm.viewModelInstance ?: return@runCatching "no view model bound"
                    null
                }.getOrElse { it.message ?: it.javaClass.simpleName }
                if (problem != null) {
                    Log.w(RiveRuntime.Tag, "Rive companion ${form.name}: $problem; using Canvas")
                    onFailure(problem)
                }
            }
        },
        onRelease = { holder.view = null; holder.viewModel = null }
    )

    // Accent colours through view-model data binding.
    LaunchedEffect(holder, palette) {
        val vm = holder.viewModel ?: return@LaunchedEffect
        val missing = holder.bindColors(vm, palette)
        if (missing.isNotEmpty()) onFailure("missing colour properties $missing")
    }

    // State, mode, calm, and the one-shot triggers.
    LaunchedEffect(holder, visual, variant) {
        val view = holder.view ?: return@LaunchedEffect
        runCatching {
            val sm = CompanionRiveContract.StateMachine
            view.setNumberState(sm, CompanionRiveContract.Inputs.State, CompanionRiveContract.stateValue(visual))
            view.setNumberState(sm, CompanionRiveContract.Inputs.Mode, CompanionRiveContract.modeValue(visual))
            view.setBooleanState(sm, CompanionRiveContract.Inputs.Calm, CompanionRiveContract.calmValue(variant))
            when {
                variant == CompanionVariant.Nod -> view.fireState(sm, CompanionRiveContract.Inputs.Nod)
                visual == CompanionState.CELEBRATING || visual == CreateMode.CELEBRATORY ->
                    view.fireState(sm, CompanionRiveContract.Inputs.Celebrate)
            }
        }.onFailure { onFailure(it.message ?: "input error") }
    }

    // Play only while visible and resumed; feed the smoothed level each frame while playing.
    LaunchedEffect(holder, active) {
        val view = holder.view ?: return@LaunchedEffect
        if (!active) { view.pause(); return@LaunchedEffect }
        view.play()
        val smoother = LevelSmoother()
        var last = 0L
        var sent = -1f
        while (true) {
            val now = awaitFrame()
            val dt = if (last == 0L) 16f else (now - last) / 1_000_000f
            last = now
            val v = CompanionRiveContract.levelValue(smoother.update(level(), dt))
            if (abs(v - sent) >= 0.5f) {
                runCatching { view.setNumberState(CompanionRiveContract.StateMachine, CompanionRiveContract.Inputs.Level, v) }
                sent = v
            }
        }
    }
}

private class RiveHolder {
    var view: RiveAnimationView? = null
    var viewModel: ViewModelInstance? = null

    /** Sets every contract colour present; returns the required ones that are missing. */
    fun bindColors(vm: ViewModelInstance, p: CompanionPalette): List<String> {
        val c = CompanionRiveContract.Colors
        val values: List<Pair<String, Color>> = listOf(
            c.Accent to p.accent, c.BodyTop to p.bodyTop, c.BodyBottom to p.bodyBottom, c.Deep to p.deep,
            c.Light to p.light, c.Rim to p.rim, c.Paper to p.paper, c.PaperLine to p.paperLine, c.Lines to p.lines,
            c.Eye to p.eye, c.Blush to p.blush, c.Sparkle to p.sparkle, c.Gold to p.gold, c.Mute to p.mute,
            c.Shadow to p.shadow, c.Halo to p.halo
        )
        val missing = mutableListOf<String>()
        for ((name, color) in values) {
            val ok = runCatching { vm.getColorProperty(name).value = color.toArgb() }.isSuccess
            if (!ok && name in c.required) missing += name
        }
        return missing
    }
}
