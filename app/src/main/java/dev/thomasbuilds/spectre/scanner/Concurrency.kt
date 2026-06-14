package dev.thomasbuilds.spectre.scanner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal fun daemonExecutor(name: String): ExecutorService =
  Executors.newSingleThreadExecutor { r ->
    Thread(r, name).apply { isDaemon = true }
  }

internal fun CoroutineScope.repeatEvery(
  periodMs: Long,
  context: CoroutineContext = EmptyCoroutineContext,
  action: suspend () -> Unit
): Job =
  launch(context) {
    while (isActive) {
      delay(periodMs)
      action()
    }
  }
