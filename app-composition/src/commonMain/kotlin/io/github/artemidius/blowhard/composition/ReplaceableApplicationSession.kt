package io.github.artemidius.blowhard.composition

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Creates a new fail-closed application session after the previous one has stopped. */
public fun interface ApplicationSessionFactory<T> {
    /** Creates a usable session or an explicit unavailable session without exposing native errors. */
    public suspend fun create(): T
}

/** Stops every resource owned by one application session. */
public fun interface ApplicationSessionCloser<T> {
    /** Stops active work and closes stores and provider resources for [session]. */
    public suspend fun close(session: T)
}

/** Erases host-owned data only after the active application session has closed. */
public fun interface ApplicationLocalDataEraser {
    /** Returns whether every local-data target was erased or already absent. */
    public suspend fun erase(): Boolean
}

/** Sanitized outcome of stopping, erasing, and replacing one application session. */
public enum class ApplicationSessionResetResult {
    /** Local data was erased and a clean replacement session was published. */
    RESET,

    /** A clean replacement session was published, but at least one data target was not erased. */
    LOCAL_DATA_ERASURE_FAILED,

    /** Another reset or host shutdown already owns the session operation. */
    ALREADY_RUNNING,

    /** The platform host closed this owner, so no reset can start. */
    OWNER_CLOSED,

    /** The active session could not be closed safely, so erasure did not start. */
    SESSION_CLOSE_FAILED,

    /** The old session closed, but a fail-closed replacement could not be created. */
    REPLACEMENT_FAILED,
}

/**
 * Owns a replaceable platform application session and enforces destructive reset ordering.
 *
 * Reset is non-cancellable once it starts: the current session closes before erasure, and a new
 * session is published before the operation reports success or an erasure failure. Concurrent
 * reset requests fail immediately instead of queuing a second destructive operation.
 */
public class ReplaceableApplicationSession<T>(
    initialSession: T,
    private val factory: ApplicationSessionFactory<T>,
    private val closer: ApplicationSessionCloser<T>,
    private val eraser: ApplicationLocalDataEraser,
) {
    private val operation = Mutex()
    private val mutableSession = MutableStateFlow(initialSession)
    private var closed = false

    /** The current session, replaced atomically after a completed reset. */
    public val session: StateFlow<T> = mutableSession.asStateFlow()

    /** Stops the current session, erases local data, and publishes a clean replacement. */
    public suspend fun reset(): ApplicationSessionResetResult {
        if (!operation.tryLock()) return ApplicationSessionResetResult.ALREADY_RUNNING
        return try {
            if (closed) {
                ApplicationSessionResetResult.OWNER_CLOSED
            } else {
                withContext(NonCancellable) { resetLocked() }
            }
        } finally {
            operation.unlock()
        }
    }

    /** Stops the current session at most once and prevents future reset operations. */
    public suspend fun close() {
        withContext(NonCancellable) {
            operation.withLock {
                if (closed) return@withLock
                closed = true
                closeSafely(mutableSession.value)
            }
        }
    }

    private suspend fun resetLocked(): ApplicationSessionResetResult = if (closeSafely(mutableSession.value)) {
        eraseAndReplace()
    } else {
        ApplicationSessionResetResult.SESSION_CLOSE_FAILED
    }

    private suspend fun eraseAndReplace(): ApplicationSessionResetResult {
        val erased = eraseSafely()
        val replacement = createSafely()
        return if (replacement == null) {
            closed = true
            ApplicationSessionResetResult.REPLACEMENT_FAILED
        } else {
            mutableSession.value = replacement
            if (erased) {
                ApplicationSessionResetResult.RESET
            } else {
                ApplicationSessionResetResult.LOCAL_DATA_ERASURE_FAILED
            }
        }
    }

    private suspend fun closeSafely(session: T): Boolean = try {
        closer.close(session)
        true
    } catch (_: Throwable) {
        false
    }

    private suspend fun eraseSafely(): Boolean = try {
        eraser.erase()
    } catch (_: Throwable) {
        false
    }

    private suspend fun createSafely(): T? = try {
        factory.create()
    } catch (_: Throwable) {
        null
    }
}
