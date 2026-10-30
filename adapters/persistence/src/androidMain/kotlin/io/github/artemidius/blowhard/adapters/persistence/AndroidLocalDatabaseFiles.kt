package io.github.artemidius.blowhard.adapters.persistence

import android.content.Context
import java.io.File

/** Deletes application-owned Android SQLite files after every store using them has closed. */
public object AndroidLocalDatabaseFiles {
    /** Deletes [name] and its SQLite sidecars without exposing filesystem details. */
    public fun delete(context: Context, name: String): LocalDatabaseDeletionResult {
        require(name.isNotBlank()) { "Database name must not be blank" }
        val application = context.applicationContext
        val files = application.getDatabasePath(name).ownedDatabaseFiles()
        if (files.none(File::exists)) return LocalDatabaseDeletionResult.NOT_FOUND

        return try {
            application.deleteDatabase(name)
            if (files.none(File::exists)) {
                LocalDatabaseDeletionResult.DELETED
            } else {
                LocalDatabaseDeletionResult.FAILED
            }
        } catch (_: RuntimeException) {
            LocalDatabaseDeletionResult.FAILED
        }
    }
}

private fun File.ownedDatabaseFiles(): List<File> = listOf(
    this,
    File("$path-journal"),
    File("$path-shm"),
    File("$path-wal"),
)
