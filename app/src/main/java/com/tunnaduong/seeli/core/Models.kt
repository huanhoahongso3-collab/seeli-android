package com.tunnaduong.seeli.core

import android.net.Uri
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.temporal.ChronoUnit

/** ISO-8601 dates, as the server sends them ("2026-11-25T15:44:26Z"). */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) =
        encoder.encodeString(value.truncatedTo(ChronoUnit.SECONDS).toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

typealias Date = @Serializable(with = InstantSerializer::class) Instant

@Serializable
enum class Audience {
    @SerialName("onlyMe") ONLY_ME,
    @SerialName("circle") CIRCLE,
    @SerialName("everyone") EVERYONE;

    val raw: String get() = when (this) { ONLY_ME -> "onlyMe"; CIRCLE -> "circle"; EVERYONE -> "everyone" }

    val badge: String get() = when (this) {
        ONLY_ME -> "ONLY ME"
        CIRCLE -> "MY CIRCLE"
        EVERYONE -> "PUBLIC"
    }

    val descriptionKey: String get() = when (this) {
        ONLY_ME -> "audience.onlyMe.desc"
        CIRCLE -> "audience.circle.desc"
        EVERYONE -> "audience.public.desc"
    }
}

private fun initialOf(name: String) = name.take(1).uppercase()

@Serializable
data class PublicUser(val id: String, val name: String, val handle: String) {
    val initial get() = initialOf(name)
}

@Serializable
data class Comment(
    val id: String,
    val text: String,
    val createdAt: Date,
    val author: PublicUser,
    /** The photo owner's answer, seen only by this friend. */
    val reply: String? = null,
    val repliedAt: Date? = null,
)

/** GET /activity: perspectives on my photos and replies to mine since a time. */
@Serializable
data class ActivityItem(val type: String, val momentId: String? = null, val at: Date, val user: PublicUser)

@Serializable
data class ActivityFeed(val now: Date, val items: List<ActivityItem>)

@Serializable
data class CommentReply(val reply: String, val repliedAt: Date)

/** A photo the user took. The server only sends `photoUrl` once `revealAt` has passed. */
@Serializable
data class Moment(
    val id: String,
    val note: String,
    val placeName: String? = null,
    val audience: Audience = Audience.ONLY_ME,
    val createdAt: Date,
    val revealAt: Date,
    val reunitedAt: Date? = null,
    val reflection: String? = null,
    val commentCount: Int = 0,
    val photoUrl: String? = null,
    /** Waiting in the outbox (taken offline / upload failed). */
    val isPending: Boolean? = null,
) {
    val isRevealed get() = reunitedAt != null
}

@Serializable
data class Friend(
    val id: String,
    val name: String,
    val handle: String,
    val bio: String = "",
    val memo: String = "",
    val connectedAt: Date,
) {
    val initial get() = initialOf(name)
}

/** A moment shared by a friend into "My Circle". Never includes the place. */
@Serializable
data class FriendPost(
    val id: String,
    val author: PublicUser,
    val note: String,
    val createdAt: Date,
    val revealed: Boolean = false,
    val photoUrl: String? = null,
    val myComment: String? = null,
    /** The author's answer to my perspective. */
    val myCommentReply: String? = null,
    val myCommentRepliedAt: Date? = null,
    val scene: Int = 0,
    /** When I opened this photo. A friend's photo can be opened only once. */
    val viewedAt: Date? = null,
)

@Serializable
data class UserAccount(
    val id: String,
    val name: String,
    val handle: String,
    val email: String,
    val bio: String = "",
    val inviteCode: String = "",
    val hasPassword: Boolean? = true,
    val providers: List<String>? = emptyList(),
) {
    val initial get() = initialOf(name)
}

/** What's being created between the camera and the audience screen. */
data class Draft(
    var imageData: ByteArray? = null,
    var capturedAt: Instant = Instant.now(),
    var note: String = "",
    var recordPlace: Boolean = true,
    var placeName: String? = null,
    var audience: Audience = Audience.ONLY_ME,
)

/** A photo waiting to be uploaded (kept on disk until the server has it). */
@Serializable
data class PendingUpload(
    val id: String,
    val note: String,
    val placeName: String? = null,
    val audience: Audience,
    val createdAt: Date,
    val demo: Boolean,
)

sealed interface Route {
    data object Camera : Route
    data object Post : Route
    data object AudienceRoute : Route
    data object Posted : Route
    data object Connect : Route
    data object Memories : Route
    data object Profile : Route
    data object Friends : Route
    data class FriendProfile(val id: String) : Route
    data object Privacy : Route
    data object Settings : Route
    data object Verified : Route
    data object Scan : Route
    data object MyQR : Route
    data class Reunion(val id: String) : Route
    data class MomentDetail(val id: String) : Route
}

/**
 * QR payload: seeli://add?c=<invite code>&n=<name>. The code is secret and rotatable,
 * so only people who actually met (and scanned it) can connect. Same format as iOS.
 */
data class FriendInvite(val code: String, val name: String) {
    val url: String
        get() = Uri.Builder().scheme("seeli").authority("add")
            .appendQueryParameter("c", code).appendQueryParameter("n", name).build().toString()

    companion object {
        fun parse(string: String): FriendInvite? {
            val trimmed = string.trim()
            runCatching { Uri.parse(trimmed) }.getOrNull()?.let { u ->
                if (u.scheme == "seeli" && u.host == "add") {
                    val code = u.getQueryParameter("c")
                    if (!code.isNullOrEmpty()) return FriendInvite(code.uppercase(), u.getQueryParameter("n") ?: "")
                }
            }
            // Manual entry of the 8-character code shown under the QR.
            val code = trimmed.uppercase().replace(" ", "").replace("-", "")
            if (code.length != 8 || !code.all { it.isLetterOrDigit() }) return null
            return FriendInvite(code, "")
        }
    }
}
