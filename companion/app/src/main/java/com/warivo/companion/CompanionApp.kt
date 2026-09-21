package com.warivo.companion

import android.app.Application
import android.content.Context
import com.warivo.companion.net.OwnerApi
import com.warivo.companion.store.OwnerStore
import org.maplibre.android.MapLibre

/** Process-wide singletons, matching the head unit's service-locator approach. */
object Owner {
    lateinit var store: OwnerStore
        private set
    lateinit var api: OwnerApi
        private set

    internal fun init(context: Context) {
        store = OwnerStore(context.applicationContext)
        // The API reads the current values on every call, so changing the server in
        // Settings takes effect immediately with nothing to rebuild.
        api = OwnerApi(baseUrl = { store.baseUrl.value }, token = { store.token.value })
    }
}

class CompanionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)      // must precede any MapView
        Owner.init(this)
    }
}
