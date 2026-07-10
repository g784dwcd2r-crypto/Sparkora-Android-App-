package com.sparkora.app

import android.app.Application
import android.content.Context
import com.sparkora.app.data.SessionManager
import com.sparkora.app.data.api.ApiProvider
import com.sparkora.app.data.repo.SparkoraRepository
import com.sparkora.app.location.LocationService

/**
 * Simple manual dependency container — one instance per process, owned by the
 * Application. Screens reach it through `LocalContext.current.appContainer()`.
 */
class AppContainer(context: Context) {
    val session = SessionManager(context)
    val apiProvider = ApiProvider(session)
    val repository = SparkoraRepository(apiProvider, session)
    val location = LocationService(context)
}

class SparkoraApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

fun Context.appContainer(): AppContainer =
    (applicationContext as SparkoraApp).container
