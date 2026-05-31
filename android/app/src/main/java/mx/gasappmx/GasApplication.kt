package mx.gasappmx

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Initializes Firebase + App Check at process start. App Check proves to the Cloud Functions
 * (and Storage) that requests come from this genuine app, not a script — see the gated proxies
 * in firebase/functions/src/. Debug builds use the debug provider (register the token printed
 * in Logcat in the Firebase console); release builds use Play Integrity.
 */
class GasApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            if (BuildConfig.DEBUG) {
                DebugAppCheckProviderFactory.getInstance()
            } else {
                PlayIntegrityAppCheckProviderFactory.getInstance()
            },
        )
    }
}
