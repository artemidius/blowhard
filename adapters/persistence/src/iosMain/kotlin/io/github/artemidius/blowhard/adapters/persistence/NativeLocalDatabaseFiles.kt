package io.github.artemidius.blowhard.adapters.persistence

import co.touchlab.sqliter.DatabaseFileContext
import platform.Foundation.NSFileManager

/** Deletes application-owned iOS SQLite files after every store using them has closed. */
public object NativeLocalDatabaseFiles {
    /** Deletes [name] and its SQLite sidecars without exposing filesystem details. */
    public fun delete(name: String): LocalDatabaseDeletionResult {
        require(name.isNotBlank()) { "Database name must not be blank" }
        val files = ownedDatabasePaths(name)
        if (files.none(::exists)) return LocalDatabaseDeletionResult.NOT_FOUND

        return try {
            DatabaseFileContext.deleteDatabase(name, null)
            if (files.none(::exists)) {
                LocalDatabaseDeletionResult.DELETED
            } else {
                LocalDatabaseDeletionResult.FAILED
            }
        } catch (_: Exception) {
            LocalDatabaseDeletionResult.FAILED
        }
    }
}

private fun ownedDatabasePaths(name: String): List<String> {
    val path = DatabaseFileContext.databasePath(name, null)
    return listOf(path, "$path-journal", "$path-shm", "$path-wal")
}

private fun exists(path: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path)
