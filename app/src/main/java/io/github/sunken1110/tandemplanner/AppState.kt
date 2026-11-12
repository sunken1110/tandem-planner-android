package io.github.sunken1110.tandemplanner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.github.sunken1110.tandemplanner.data.SharedCalendar
import io.github.sunken1110.tandemplanner.data.SharedChecklists

/**
 * The app's shared data, kept for as long as the screen is open: a ViewModel
 * outlives the Activity being recreated (rotation, dark-mode switch, a language
 * change), so the calendar and checklists aren't rebuilt each time. Rebuilding
 * them reattached every listener and lost what they held, such as an error
 * message on screen or "just created" (which opens the share dialog once).
 */
class AppState(application: Application) : AndroidViewModel(application) {
    val calendar = SharedCalendar(application)
    val checklists = SharedChecklists(calendar, application)
}
