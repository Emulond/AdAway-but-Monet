package org.adaway.tile

import android.service.quicksettings.Tile.STATE_ACTIVE
import android.service.quicksettings.Tile.STATE_INACTIVE
import android.service.quicksettings.TileService
import androidx.lifecycle.Observer
import org.adaway.AdAwayApplication
import org.adaway.helper.PreferenceHelper
import org.adaway.model.adblocking.AdBlockModel
import org.adaway.model.error.HostErrorException
import org.adaway.util.CoroutineDispatchers
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class AdBlockingTileService : TileService() {
    private val toggling = AtomicBoolean(false)

    /**
     * Held in a field because a method reference produces a new instance every time it is
     * evaluated, so removing the observer with a fresh reference would never match the one that
     * was added.
     */
    private val appliedObserver = Observer<Boolean> { applied ->
        PreferenceHelper.setLastKnownAdBlocked(this, applied == true)
        updateTile(applied == true)
    }

    override fun onTileAdded() {
        updateTile(currentState())
    }

    override fun onStartListening() {
        // Render from the last known state first: building the ad block model opens a privileged
        // shell, and this runs every time the quick settings panel is expanded.
        updateTile(currentState())
        createdModel?.isApplied?.observeForever(appliedObserver)
    }

    override fun onStopListening() {
        createdModel?.isApplied?.removeObserver(appliedObserver)
    }

    override fun onClick() {
        CoroutineDispatchers.ioExecutor().execute(::toggleAdBlocking)
    }

    private fun currentState(): Boolean {
        return createdModel?.isApplied?.value ?: PreferenceHelper.getLastKnownAdBlocked(this)
    }

    private fun updateTile(adBlocked: Boolean) {
        qsTile?.let { tile ->
            tile.state = if (adBlocked) STATE_ACTIVE else STATE_INACTIVE
            tile.updateTile()
        }
    }

    private fun toggleAdBlocking() {
        if (toggling.getAndSet(true)) {
            return
        }
        try {
            // Only here is the model built on demand: acting on the tile is an explicit request.
            val model = model
            val applied = model.isApplied.value == true
            if (applied) {
                model.revert()
            } else {
                model.apply()
            }
            PreferenceHelper.setLastKnownAdBlocked(this, !applied)
            updateTile(!applied)
        } catch (exception: HostErrorException) {
            Timber.w(exception, "Failed to toggle ad-blocking.")
        } finally {
            toggling.set(false)
        }
    }

    private val model: AdBlockModel
        get() = (application as AdAwayApplication).adBlockModel

    private val createdModel: AdBlockModel?
        get() = (application as AdAwayApplication).adBlockModelIfCreated
}
