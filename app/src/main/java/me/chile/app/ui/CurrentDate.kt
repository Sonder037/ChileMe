package me.chile.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay

// Refresh on return to the app and across midnight; no timer while backgrounded.
@Composable internal fun rememberCurrentTime(clock: Clock? = null): LocalDateTime {
    fun current()=LocalDateTime.now(clock?:Clock.systemDefaultZone())
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val today by produceState(current(),clock,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true) {value=current();delay(60_000)}
        }
    }
    return today
}

@Composable internal fun rememberToday(clock: Clock? = null): LocalDate = rememberCurrentTime(clock).toLocalDate()
