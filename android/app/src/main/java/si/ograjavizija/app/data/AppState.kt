package si.ograjavizija.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import java.io.File

/** Globalno (procesno) stanje: trenutni projekt in nastavitve strežnika. */
object AppState {
    private lateinit var appCtx: Context
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    var currentProject by mutableStateOf<Project?>(null)
        private set
    var serverUrl by mutableStateOf("")
        private set
    var inquiryToken by mutableStateOf("")
        private set
    var lastMessage by mutableStateOf<String?>(null)

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        val f = File(appCtx.filesDir, "settings.json")
        if (f.exists()) runCatching {
            val s = json.decodeFromString<UiSettings>(f.readText())
            serverUrl = s.serverUrl
            inquiryToken = s.inquiryToken
        }
    }

    fun setProject(p: Project?) { currentProject = p }

    fun updateServerUrl(url: String) {
        serverUrl = url.trim().trimEnd('/')
        persist()
    }

    fun updateInquiryToken(token: String) {
        inquiryToken = token.trim()
        persist()
    }

    private fun persist() {
        runCatching {
            File(appCtx.filesDir, "settings.json")
                .writeText(json.encodeToString(UiSettings.serializer(), UiSettings(serverUrl = serverUrl, inquiryToken = inquiryToken)))
        }
    }
}

@kotlinx.serialization.Serializable
data class UiSettings(val serverUrl: String = "", val inquiryToken: String = "")
