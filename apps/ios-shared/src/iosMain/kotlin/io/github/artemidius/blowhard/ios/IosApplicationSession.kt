package io.github.artemidius.blowhard.ios

import io.github.artemidius.blowhard.adapters.mistral.MistralChatConfiguration
import io.github.artemidius.blowhard.adapters.mistral.MistralDocumentOcrPort
import io.github.artemidius.blowhard.adapters.mistral.MistralLiveTextGenerationPort
import io.github.artemidius.blowhard.adapters.mistral.MistralMeetingNoteGenerationPort
import io.github.artemidius.blowhard.adapters.mistral.MistralModelCatalog
import io.github.artemidius.blowhard.adapters.mistral.asMistralCredentialProvider
import io.github.artemidius.blowhard.adapters.mobile.IosCredentialStores
import io.github.artemidius.blowhard.adapters.persistence.NativeConversationStores
import io.github.artemidius.blowhard.composition.ApplicationRuntime
import io.github.artemidius.blowhard.composition.ApplicationRuntimeConfiguration
import io.github.artemidius.blowhard.composition.ApplicationRuntimeDependencies
import io.github.artemidius.blowhard.composition.ConversationHistorySession
import io.github.artemidius.blowhard.composition.LocalMeetingNoteApprovalPort
import io.github.artemidius.blowhard.composition.OwnedMeetingNoteGenerationPort
import io.github.artemidius.blowhard.composition.VerifiedModelMeetingNoteGenerationRouter
import io.github.artemidius.blowhard.core.application.DefaultConnectionSettings
import io.github.artemidius.blowhard.core.application.SequentialRunIdFactory
import platform.Foundation.NSDate
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970

/** Owns one iOS runtime together with the native resources supplied to it. */
internal class IosApplicationSession(
    val runtime: ApplicationRuntime,
    private val history: ConversationHistorySession,
    private val documents: IosDocumentSession?,
    private val providers: IosProviderComposition,
) {
    suspend fun close() {
        try {
            runtime.close()
        } finally {
            try {
                history.close()
            } finally {
                try {
                    documents?.close()
                } finally {
                    providers.close()
                }
            }
        }
    }
}

@Suppress("TooGenericExceptionCaught")
internal suspend fun createIosApplicationSession(documentPicker: IosDocumentPicker): IosApplicationSession {
    val providers = createProviderComposition()
    var history: ConversationHistorySession? = null
    var documents: IosDocumentSession? = null
    return try {
        val openedHistory = ConversationHistorySession.open {
            NativeConversationStores.open(CONVERSATION_DATABASE_NAME)
        }
        history = openedHistory
        documents = IosDocumentSession.openOrNull(
            processor = providers.documentOcr,
            meetingNoteGeneration = providers.meetingNoteGeneration,
        )
        val dependencies = createDependencies(providers, openedHistory, documents, documentPicker)
        val runtime = ApplicationRuntime.create(
            dependencies = dependencies,
            configuration = ApplicationRuntimeConfiguration(
                initialState = openedHistory.initialState,
                runIdFactory = SequentialRunIdFactory("ios-${NSUUID().UUIDString}"),
                autoVerifyCredential = true,
            ),
        )
        IosApplicationSession(runtime, openedHistory, documents, providers)
    } catch (failure: RuntimeException) {
        try {
            history?.close()
        } finally {
            try {
                documents?.close()
            } finally {
                providers.close()
            }
        }
        throw failure
    }
}

private fun createDependencies(
    providers: IosProviderComposition,
    history: ConversationHistorySession,
    documents: IosDocumentSession?,
    documentPicker: IosDocumentPicker,
): ApplicationRuntimeDependencies {
    val base = ApplicationRuntimeDependencies.conversationOnly(
        providers.connectionSettings,
        providers.generation,
        history.conversationStore,
    )
    return documents?.let {
        val approvals = LocalMeetingNoteApprovalPort.create(
            store = it.meetingNoteStore,
            now = ::currentApprovalTimeMillis,
            identity = { NSUUID().UUIDString },
        )
        base.withDocuments(it.library, it.ocr, documentPicker, it.processingStore)
            .withMeetingNotes(it.meetingNotes, approvals)
    } ?: base
}

private fun currentApprovalTimeMillis(): Long = (NSDate().timeIntervalSince1970() * MILLISECONDS_PER_SECOND).toLong()

internal class IosProviderComposition(
    val connectionSettings: DefaultConnectionSettings,
    val generation: MistralLiveTextGenerationPort,
    val documentOcr: MistralDocumentOcrPort,
    val meetingNoteGeneration: VerifiedModelMeetingNoteGenerationRouter,
    private val modelCatalog: MistralModelCatalog,
) {
    suspend fun close() {
        try {
            meetingNoteGeneration.close()
        } finally {
            try {
                documentOcr.close()
            } finally {
                modelCatalog.close()
            }
        }
    }
}

private fun createProviderComposition(): IosProviderComposition {
    val credentialStore = IosCredentialStores.mistralApiKey()
    val credentialProvider = credentialStore.asMistralCredentialProvider()
    val modelCatalog = MistralModelCatalog.create(credentialProvider)
    val documentOcr = MistralDocumentOcrPort.create(credentialProvider)
    val connectionSettings = DefaultConnectionSettings(
        credentials = credentialStore,
        catalog = modelCatalog,
        startInLiveMode = true,
    )
    val meetingNoteGeneration = VerifiedModelMeetingNoteGenerationRouter(connectionSettings) { modelId ->
        val port = MistralMeetingNoteGenerationPort.create(
            credentialProvider,
            MistralChatConfiguration(modelId),
        )
        OwnedMeetingNoteGenerationPort(port) { port.close() }
    }
    return IosProviderComposition(
        connectionSettings = connectionSettings,
        generation = MistralLiveTextGenerationPort.create(credentialProvider),
        documentOcr = documentOcr,
        meetingNoteGeneration = meetingNoteGeneration,
        modelCatalog = modelCatalog,
    )
}

private const val MILLISECONDS_PER_SECOND = 1_000.0
