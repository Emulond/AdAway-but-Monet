package org.adaway.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout

/**
 * Resolves the view a [com.google.android.material.snackbar.Snackbar] should be attached to.
 *
 * Material only installs its swipe to dismiss behaviour when the snackbar parent is a
 * [CoordinatorLayout]. The screens are built with Compose and no longer contain one, so a
 * snackbar made against the Compose view cannot be swiped away. Since these snackbars are shown
 * with `LENGTH_INDEFINITE`, they also never time out, leaving the action button as the only way to
 * get rid of them.
 *
 * This installs a single transparent [CoordinatorLayout] over the activity content and reuses it
 * for every snackbar. It hosts no clickable children of its own, so touches it does not handle
 * fall through to the Compose hierarchy below.
 */
object SnackbarParent {
    private const val TAG = "adaway:snackbar-parent"

    @JvmStatic
    fun of(view: View): View {
        findExistingCoordinator(view)?.let { return it }
        val content = findActivity(view.context)?.findViewById<ViewGroup>(android.R.id.content)
            ?: return view
        content.findViewWithTag<CoordinatorLayout>(TAG)?.let { return it }
        return CoordinatorLayout(content.context).apply {
            tag = TAG
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isClickable = false
            isFocusable = false
            content.addView(this)
        }
    }

    private fun findExistingCoordinator(view: View): CoordinatorLayout? {
        var current: View? = view
        while (current != null) {
            if (current is CoordinatorLayout) {
                return current
            }
            current = current.parent as? View
        }
        return null
    }

    private fun findActivity(context: Context): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) {
                return current
            }
            current = current.baseContext
        }
        return null
    }
}
