package app.nagmedar.ui.theme

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Keeps View [Animator]s (a `ValueAnimator` on a seek bar, a waveform, a cover) from outliving
 * their screen (Desktop Kit 1.0): when the composition (or the view) goes away, or the
 * configuration changes (a DeX resize, a rotation, a theme switch), every running animator is
 * cancelled and jumps to its end value, so nothing is left half-way with stale sizes. With
 * reduced motion ([SystemMotion.reduced]) [start] ends the animator at once.
 *
 * In Compose: `val animators = rememberViewAnimators(); animators.start(ValueAnimator.ofFloat(…))`.
 * In View code: `ViewAnimators.bind(view).start(animator)`.
 */
class ViewAnimators internal constructor(private val context: Context) {
    private val running = LinkedHashSet<Animator>()

    /** Tracks [animator], starts it and returns it; with reduced motion it ends immediately. */
    fun <A : Animator> start(animator: A): A {
        track(animator)
        animator.start()
        if (SystemMotion.reduced(context)) animator.end()
        return animator
    }

    /** Tracks an animator the caller starts itself. */
    fun <A : Animator> track(animator: A): A {
        if (running.add(animator)) {
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    running.remove(animation)
                    animation.removeListener(this)
                }
            })
        }
        return animator
    }

    /** Cancels every running animator and puts it at its end value. */
    fun endAll() {
        running.toList().forEach { endAtFinalValue(it) }
        running.clear()
    }

    companion object {
        /**
         * Animators tied to [view]: they end when the view is detached from its window or the
         * configuration changes. Keep the returned object for the view's lifetime.
         */
        fun bind(view: View): ViewAnimators {
            val animators = ViewAnimators(view.context)
            val callbacks = object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) = animators.endAll()

                @Deprecated("Deprecated in Java")
                override fun onLowMemory() = Unit
            }
            val attach = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.context.registerComponentCallbacks(callbacks)
                }

                override fun onViewDetachedFromWindow(v: View) {
                    animators.endAll()
                    v.context.unregisterComponentCallbacks(callbacks)
                }
            }
            view.addOnAttachStateChangeListener(attach)
            if (view.isAttachedToWindow) attach.onViewAttachedToWindow(view)
            return animators
        }

        /** Cancels [animator] and leaves it at its end value (`end()` runs the end listeners as well). */
        fun endAtFinalValue(animator: Animator) {
            if (animator.isStarted || animator.isRunning) animator.end()
        }
    }
}

/** [ViewAnimators] for this composition: ended on dispose and whenever the configuration changes. */
@Composable
fun rememberViewAnimators(): ViewAnimators {
    val context = LocalContext.current
    val animators = remember(context) { ViewAnimators(context) }
    val configuration = LocalConfiguration.current
    // a new Configuration object arrives with every change (size, density, night mode, …)
    DisposableEffect(configuration) {
        onDispose { animators.endAll() }
    }
    return animators
}
