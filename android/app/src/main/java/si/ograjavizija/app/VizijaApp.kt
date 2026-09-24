package si.ograjavizija.app

import android.app.Application
import si.ograjavizija.app.data.AppState
import si.ograjavizija.app.data.ProjectStore

class VizijaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppState.init(this)
        ProjectStore.init(this)
    }
}
