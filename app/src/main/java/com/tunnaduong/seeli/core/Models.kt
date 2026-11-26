package com.tunnaduong.seeli.core

import android.net.Uri
import kotlinx.serialization.KSerializer
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

/** Unknown values (from a newer server) read as circle rather than failing the whole list. */
object AudienceSerializer : KSerializer<Audience> {
    override val descriptor = PrimitiveSerialDescriptor("Audience", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Audience) = encoder.encodeString(value.raw)
    override fun deserialize(decoder: Decoder): Audience =
        decoder.decodeString().let { raw -> Audience.entries.firstOrNull { it.raw == raw } ?: Audience.CIRCLE }
}

@Serializable(with = AudienceSerializer::class)
enum class Audience(val raw: String) {
    ONLY_ME("onlyMe"),
    CLOSE_FRIENDS("closeFriends"),
    SELECTED("selected"),
    CIRCLE("circle"),
    EVERYONE("everyone");

    val badge: String get() = when (this) {
        ONLY_ME -> "ONLY ME"
        CLOSE_FRIENDS -> "CLOSE FRIENDS"
        SELECTED -> "CHOSEN"
        CIRCLE -> "MY CIRCLE"
        EVERYONE -> "PUBLIC"
    }

    val descriptionKey: String get() = when (this) {
        ONLY_ME -> "audience.onlyMe.desc"
        CLOSE_FRIENDS -> "audience.close.desc"
        SELECTED -> "audience.selected.desc"
        CIRCLE -> "audience.circle.desc"
        EVERYONE -> "audience.public.desc"
    }
}

/** Who a moment is shared with (newer servers); `audience` alone is only onlyMe / circle. */
@Serializable
data class ShareInfo(val audience: Audience, val recipientIds: List<String> = emptyList())

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

/** GET /activity?v=2: comments on my photos, answers to mine, friend requests. */
@Serializable
data class ActivityItem(
    val type: String,
    val momentId: String? = null,
    val at: Date,
    val user: PublicUser,
    val commentId: String? = null,
    val text: String? = null,
    val pending: Boolean? = null,
) {
    val key get() = "$type-${commentId ?: momentId ?: ""}-${user.id}-${at.epochSecond}"
}

/** One comment in a moment's shared thread (GET comments?v=2). */
@Serializable
data class ThreadComment(
    val id: String,
    val parentId: String? = null,
    val text: String,
    val createdAt: Date,
    val editedAt: Date? = null,
    val author: PublicUser,
    /** "owner" (only the photo's owner) or "circle" (everyone, once approved). */
    val visibility: String = "owner",
    /** "pending" | "approved" | "rejected". */
    val status: String = "approved",
    val mine: Boolean = false,
) {
    val isPending get() = visibility == "circle" && status == "pending"
}

@Serializable
data class CommentThread(val isOwner: Boolean, val comments: List<ThreadComment>)

@Serializable
data class FriendRequest(val user: PublicUser, val createdAt: Date)

@Serializable
data class FriendRequests(val incoming: List<FriendRequest> = emptyList(), val outgoing: List<FriendRequest> = emptyList())

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
    val share: ShareInfo? = null,
    /** Comments meant for the circle that wait for my approval. */
    val pendingCount: Int? = null,
    val lastCommentAt: Date? = null,
) {
    val isRevealed get() = reunitedAt != null
    val sharedWith get() = share?.audience ?: audience
}

@Serializable
data class Friend(
    val id: String,
    val name: String,
    val handle: String,
    val bio: String = "",
    val memo: String = "",
    val connectedAt: Date,
    val close: Boolean? = false,
) {
    val isClose get() = close == true
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
    /** Comments I can see, mine, and answers to mine. */
    val commentCount: Int? = null,
    val myCommentCount: Int? = null,
    val repliesToMe: Int? = null,
    /** Last time something concerning me happened here. */
    val lastActivityAt: Date? = null,
) {
    val hasMyActivity get() = (myCommentCount ?: 0) > 0 || (repliesToMe ?: 0) > 0 || myComment != null
}

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
    var recipients: List<String> = emptyList(),
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
    val recipients: List<String>? = null,
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
    data object Requests : Route
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
