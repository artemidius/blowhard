package io.github.artemidius.blowhard.core.api

/** Versioned, validated content extracted from one immutable source document. */
public data class MeetingNoteContent(
    public val title: String?,
    public val decisions: List<MeetingDecision>,
    public val actionItems: List<MeetingActionItem>,
) {
    init {
        require(title == null || title.isValidMeetingNoteText(MAX_TITLE_LENGTH)) {
            "Meeting-note title is invalid"
        }
        require(decisions.size <= MAX_ITEMS) { "Meeting note has too many decisions" }
        require(actionItems.size <= MAX_ITEMS) { "Meeting note has too many action items" }
    }

    /** Validation limits shared by structured-output and persistence boundaries. */
    public companion object {
        /** Maximum title length in Kotlin characters. */
        public const val MAX_TITLE_LENGTH: Int = 200

        /** Maximum number of decisions or action items in one note. */
        public const val MAX_ITEMS: Int = 100
    }
}

/** One decision supported by exact excerpts from the source extraction. */
public data class MeetingDecision(public val summary: String, public val sources: List<MeetingNoteSourceReference>) {
    init {
        require(summary.isValidMeetingNoteText(MAX_SUMMARY_LENGTH)) { "Decision summary is invalid" }
        require(sources.isNotEmpty() && sources.size <= MAX_SOURCES) { "Decision sources are invalid" }
    }

    /** Limits applied to one decision. */
    public companion object {
        /** Maximum summary length in Kotlin characters. */
        public const val MAX_SUMMARY_LENGTH: Int = 2_000

        /** Maximum source references attached to one item. */
        public const val MAX_SOURCES: Int = 8
    }
}

/** One action item. Unknown owners and dates remain null rather than being invented. */
public data class MeetingActionItem(
    public val description: String,
    public val owner: String?,
    public val dueDate: MeetingNoteDate?,
    public val sources: List<MeetingNoteSourceReference>,
) {
    init {
        require(description.isValidMeetingNoteText(MAX_DESCRIPTION_LENGTH)) {
            "Action-item description is invalid"
        }
        require(owner == null || owner.isValidMeetingNoteText(MAX_OWNER_LENGTH)) {
            "Action-item owner is invalid"
        }
        require(sources.isNotEmpty() && sources.size <= MeetingDecision.MAX_SOURCES) {
            "Action-item sources are invalid"
        }
    }

    /** Limits applied to one action item. */
    public companion object {
        /** Maximum action description length in Kotlin characters. */
        public const val MAX_DESCRIPTION_LENGTH: Int = 2_000

        /** Maximum owner length in Kotlin characters. */
        public const val MAX_OWNER_LENGTH: Int = 200
    }
}

/** Exact, locally resolvable evidence for one extracted item. */
public data class MeetingNoteSourceReference(public val pageIndex: Int, public val excerpt: String) {
    init {
        require(pageIndex >= 0) { "Source page index must not be negative" }
        require(excerpt.isValidMeetingNoteText(MAX_EXCERPT_LENGTH)) { "Source excerpt is invalid" }
    }

    /** Limits applied to one source reference. */
    public companion object {
        /** Maximum source excerpt length in Kotlin characters. */
        public const val MAX_EXCERPT_LENGTH: Int = 500
    }
}

/** Strict ISO-8601 calendar date used by meeting-note action items. */
public data class MeetingNoteDate(public val value: String) {
    init {
        require(value.isValidIsoDate()) { "Meeting-note date must be a valid YYYY-MM-DD date" }
    }

    override fun toString(): String = value
}

private fun String.isValidMeetingNoteText(maxLength: Int): Boolean =
    isNotBlank() && length <= maxLength && none { it == '\u0000' }

private fun String.isValidIsoDate(): Boolean {
    val match = ISO_DATE_PATTERN.matchEntire(this) ?: return false
    val year = match.groupValues[YEAR_GROUP].toInt()
    val month = match.groupValues[MONTH_GROUP].toInt()
    val day = match.groupValues[DAY_GROUP].toInt()
    val monthLength = when (month) {
        FEBRUARY -> if (year.isLeapYear()) LEAP_FEBRUARY_DAYS else FEBRUARY_DAYS
        APRIL, JUNE, SEPTEMBER, NOVEMBER -> SHORT_MONTH_DAYS
        else -> LONG_MONTH_DAYS
    }
    return year > 0 && month in JANUARY..DECEMBER && day in 1..monthLength
}

private fun Int.isLeapYear(): Boolean =
    this % LEAP_DIVISOR == 0 && (this % CENTURY_DIVISOR != 0 || this % FOUR_CENTURY_DIVISOR == 0)

private val ISO_DATE_PATTERN = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")
private const val YEAR_GROUP = 1
private const val MONTH_GROUP = 2
private const val DAY_GROUP = 3
private const val JANUARY = 1
private const val FEBRUARY = 2
private const val APRIL = 4
private const val JUNE = 6
private const val SEPTEMBER = 9
private const val NOVEMBER = 11
private const val DECEMBER = 12
private const val FEBRUARY_DAYS = 28
private const val LEAP_FEBRUARY_DAYS = 29
private const val SHORT_MONTH_DAYS = 30
private const val LONG_MONTH_DAYS = 31
private const val LEAP_DIVISOR = 4
private const val CENTURY_DIVISOR = 100
private const val FOUR_CENTURY_DIVISOR = 400
