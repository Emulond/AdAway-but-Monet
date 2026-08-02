package org.adaway;

import android.app.Application;

import org.adaway.helper.NotificationHelper;
import org.adaway.helper.PreferenceHelper;
import org.adaway.helper.ProgressNotifications;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.source.SourceModel;
import org.adaway.model.update.UpdateModel;
import org.adaway.util.log.ApplicationLog;

/**
 * This class is a custom {@link Application} for AdAway app.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class AdAwayApplication extends Application {
    /**
     * The common source model for the whole application.
     */
    private SourceModel sourceModel;
    /**
     * The common ad block model for the whole application.
     */
    private AdBlockModel adBlockModel;
    /**
     * The common update model for the whole application.
     */
    private UpdateModel updateModel;

    @Override
    public void onCreate() {
        // Delegate application creation
        super.onCreate();
        // Initialize logging
        ApplicationLog.init(this);
        // Create notification channels
        NotificationHelper.createNotificationChannels(this);
        // Clears progress notifications left by a previous process and follows the
        // application state so they are only shown while it is not visible.
        ProgressNotifications.init(this);
        // Create models
        this.sourceModel = new SourceModel(this);
        this.updateModel = new UpdateModel(this);
    }

    /**
     * Get the source model.
     *
     * @return The common source model for the whole application.
     */
    public SourceModel getSourceModel() {
        return this.sourceModel;
    }

    /**
     * Get the ad block model.
     *
     * @return The common ad block model for the whole application.
     */
    public AdBlockModel getAdBlockModel() {
        // Check cached model
        AdBlockMethod method = PreferenceHelper.getAdBlockMethod(this);
        if (this.adBlockModel == null || this.adBlockModel.getMethod() != method) {
            this.adBlockModel = AdBlockModel.build(this, method);
        }
        return this.adBlockModel;
    }

    /**
     * Get the ad block model only if it was already created.
     * Building it opens a privileged shell, so callers that merely display a state, such as the
     * quick settings tiles, must not trigger it.
     *
     * @return The common ad block model, or {@code null} if it was not created yet.
     */
    public AdBlockModel getAdBlockModelIfCreated() {
        AdBlockMethod method = PreferenceHelper.getAdBlockMethod(this);
        if (this.adBlockModel != null && this.adBlockModel.getMethod() == method) {
            return this.adBlockModel;
        }
        return null;
    }

    /**
     * Get the update model.
     *
     * @return Teh common update model for the whole application.
     */
    public UpdateModel getUpdateModel() {
        return this.updateModel;
    }
}
