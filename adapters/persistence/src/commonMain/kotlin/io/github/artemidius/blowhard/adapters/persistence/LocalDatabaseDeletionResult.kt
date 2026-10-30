package io.github.artemidius.blowhard.adapters.persistence

/** Sanitized result of deleting one application-owned SQLite database and its sidecars. */
public enum class LocalDatabaseDeletionResult {
    /** At least one database file existed and every owned file was removed. */
    DELETED,

    /** No primary database, journal, WAL, or shared-memory file existed. */
    NOT_FOUND,

    /** Deletion failed or at least one owned file remains. */
    FAILED,
}
