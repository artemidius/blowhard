package io.github.artemidius.blowhard.ui

/** Root destinations exposed by the mobile navigation bar. */
internal enum class MobileRootDestination {
    CHATS,
    DOCUMENTS,
    SETTINGS,
    ;

    internal val start: MobileDestination
        get() = when (this) {
            CHATS -> MobileDestination.CONVERSATION
            DOCUMENTS -> MobileDestination.DOCUMENTS
            SETTINGS -> MobileDestination.SETTINGS
        }
}

/** Screens owned by the mobile workspace. */
internal enum class MobileDestination(
    internal val route: String,
    internal val root: MobileRootDestination,
    internal val isRoot: Boolean = false,
) {
    CONVERSATION("conversation", MobileRootDestination.CHATS, isRoot = true),
    CONVERSATION_LIST("conversation-list", MobileRootDestination.CHATS),
    RUN_INSPECTOR("run-inspector", MobileRootDestination.CHATS),
    DOCUMENTS("documents", MobileRootDestination.DOCUMENTS, isRoot = true),
    MEETING_NOTE("meeting-note", MobileRootDestination.DOCUMENTS),
    SETTINGS("settings", MobileRootDestination.SETTINGS, isRoot = true),
    ;

    internal companion object {
        internal fun fromRoute(route: String): MobileDestination? = entries.firstOrNull { it.route == route }
    }
}

/** Immutable, bounded navigation state for phone and tablet clients. */
@ConsistentCopyVisibility
internal data class MobileNavigationState internal constructor(private val destinations: List<MobileDestination>) {
    init {
        require(destinations.size in 1..2) { "Mobile navigation keeps one root and at most one detail" }
        require(destinations.first().isRoot) { "Mobile navigation must start at a root" }
        require(destinations.drop(1).all { !it.isRoot && it.root == destinations.first().root }) {
            "Mobile navigation details must belong to their root"
        }
    }

    internal val current: MobileDestination
        get() = destinations.last()

    internal val selectedRoot: MobileRootDestination
        get() = destinations.first().root

    internal fun selectRoot(root: MobileRootDestination): MobileNavigationState =
        MobileNavigationState(listOf(root.start))

    internal fun open(destination: MobileDestination): MobileNavigationState {
        require(!destination.isRoot) { "Use selectRoot for root destinations" }
        require(destination.root == selectedRoot) { "Details must belong to the selected root" }
        return MobileNavigationState(listOf(destinations.first(), destination))
    }

    internal fun back(): MobileNavigationState =
        if (destinations.size == 1) this else MobileNavigationState(listOf(destinations.first()))

    internal fun savedRoutes(): List<String> = destinations.map(MobileDestination::route)

    internal companion object {
        internal fun initial(): MobileNavigationState = MobileNavigationState(listOf(MobileRootDestination.CHATS.start))

        internal fun restore(routes: List<String>): MobileNavigationState {
            val root = routes.firstOrNull()
                ?.let(MobileDestination::fromRoute)
                ?.takeIf(MobileDestination::isRoot)
                ?: return initial()
            val detail = routes.getOrNull(1)
                ?.let(MobileDestination::fromRoute)
                ?.takeIf { !it.isRoot && it.root == root.root }
            return MobileNavigationState(listOfNotNull(root, detail))
        }
    }
}
