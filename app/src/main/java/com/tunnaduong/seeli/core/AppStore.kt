package com.tunnaduong.seeli.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tunnaduong.seeli.widget.SeeliWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * App state. The server (seeli-api) is the source of truth; this keeps a local cache so the
 * app opens instantly and works offline, plus an outbox for photos. Mirrors AppStore.swift.
 */
object AppStore {
    private lateinit var ctx: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val api = ApiClient

    // Auth
    var account by mutableStateOf<UserAccount?>(null)
    private var authGeneration by mutableStateOf(0)
    val isLoggedIn: Boolean get() { authGeneration; return account != null && api.token != null }

    // Content
    var moments by mutableStateOf(listOf<Moment>())
    var friends by mutableStateOf(listOf<Friend>())
    var friendPosts by mutableStateOf(listOf<FriendPost>())
    /** Friends' photos opened in this visit to Let's connect. Memory only, never saved. */
    val onceImages = mutableStateMapOf<String, Bitmap>()
    var blocked by mutableStateOf(listOf<PublicUser>())
    var outbox by mutableStateOf(listOf<PendingUpload>())

    // Preferences
    var defaultAudience by mutableStateOf(Audience.ONLY_ME)
    var demoMode by mutableStateOf(false)
    /** Whether the server allows demo mode (ALLOW_DEMO). Hidden in Settings when false. */
    var serverAllowsDemo by mutableStateOf(false)
    var notificationsEnabled by mutableStateOf(true)
    /** Notify when a friend shares to the circle. */
    var friendPushEnabled by mutableStateOf(true)

    // Session (not persisted)
    var draft = Draft()
    val path = mutableStateListOf<Route>()
    var now by mutableStateOf(Instant.now())
    var pendingInvite by mutableStateOf<FriendInvite?>(null)
    /** A moment whose comments should open (tapped a "new perspective" notification). */
    var pendingCommentsMoment by mutableStateOf<String?>(null)
    var isSyncing by mutableStateOf(false)
    /** True right after sign-up, so the "account created" screen shows before entering. */
    var welcomePending by mutableStateOf(false)
    var lastError by mutableStateOf<String?>(null)
    /** Asked by the store, answered by the activity (runtime permission dialogs live there). */
    var permissionRequest by mutableStateOf<String?>(null)

    @Serializable
    private data class Health(val allowDemo: Boolean? = null)

    @Serializable
    private data class Snapshot(
        val account: UserAccount? = null,
        val moments: List<Moment> = emptyList(),
        val friends: List<Friend> = emptyList(),
        val friendPosts: List<FriendPost> = emptyList(),
        val blocked: List<PublicUser> = emptyList(),
        val outbox: List<PendingUpload> = emptyList(),
        val defaultAudience: Audience = Audience.ONLY_ME,
        val demoMode: Boolean = false,
        val notificationsEnabled: Boolean = true,
        val friendPushEnabled: Boolean? = true,
    )

    private val cacheFile get() = File(ctx.filesDir, "seeli-cache.json")
    private val outboxDir get() = File(ctx.filesDir, "outbox").apply { mkdirs() }

    fun init(context: Context) {
        ctx = context.applicationContext
        api.onUnauthorized = { if (account != null) clearSession() }
        load()
        updateWidgets()
    }

    // ---- Persistence

    private fun load() {
        val s = runCatching { ApiClient.json.decodeFromString(Snapshot.serializer(), cacheFile.readText()) }.getOrNull() ?: return
        account = s.account; moments = s.moments; friends = s.friends; friendPosts = s.friendPosts
        blocked = s.blocked; outbox = s.outbox; defaultAudience = s.defaultAudience; demoMode = s.demoMode
        notificationsEnabled = s.notificationsEnabled; friendPushEnabled = s.friendPushEnabled ?: true
    }

    fun save() {
        val s = Snapshot(account, moments, friends, friendPosts, blocked, outbox, defaultAudience, demoMode,
            notificationsEnabled, friendPushEnabled)
        runCatching {
            val tmp = File(ctx.filesDir, "seeli-cache.json.tmp")
            tmp.writeText(ApiClient.json.encodeToString(Snapshot.serializer(), s))
            tmp.renameTo(cacheFile)
        }
        ctx.getSharedPreferences("seeli.prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("seeli.friendPush", friendPushEnabled).apply()
        updateWidgets()
    }

    /** Words, dates and counts (never photos) for the home-screen widgets. */
    fun updateWidgets() {
        val snap = if (isLoggedIn) WidgetSnapshot(
            loggedIn = true,
            pendingReveals = allMoments.filter { it.reunitedAt == null }.map { it.revealAt },
            words = allMoments.filter { it.note.isNotEmpty() }.sortedByDescending { it.createdAt }.take(30)
                .map { WidgetSnapshot.Word(it.note, it.createdAt, it.placeName) },
        ) else WidgetSnapshot()
        WidgetSnapshot.save(ctx, snap)
        scope.launch { SeeliWidgets.updateAll(ctx) }
    }

    // ---- Errors

    /** Runs an API call, turning failures into a user-facing message. */
    suspend fun <T> attempt(work: suspend () -> T): T? = try {
        work().also { lastError = null }
    } catch (e: Exception) {
        lastError = e.userMessage(); null
    }

    fun launch(block: suspend CoroutineScope.() -> Unit) = scope.launch(block = block)

    // ---- Auth

    @Serializable
    private data class AuthResponse(val token: String, val user: UserAccount, val isNew: Boolean? = null)

    suspend fun signUp(name: String, handle: String, email: String, password: String) {
        val res = api.request<AuthResponse>("POST", "auth/signup",
            mapOf("name" to name, "handle" to handle, "email" to email, "password" to password))
        welcomePending = true
        startSession(res)
    }

    /** Sign in with Google. Returns true when a new SEELI account was created. */
    suspend fun signIn(c: SocialCredential): Boolean {
        val res = api.request<AuthResponse>("POST", "auth/${c.provider}",
            mapOf("idToken" to c.idToken, "nonce" to c.nonce, "name" to c.name))
        val isNew = res.isNew ?: false
        welcomePending = isNew
        startSession(res)
        if (!isNew) refresh()
        return isNew
    }

    suspend fun login(email: String, password: String) {
        val res = api.request<AuthResponse>("POST", "auth/login", mapOf("email" to email, "password" to password))
        startSession(res)
        refresh()
    }

    private fun startSession(res: AuthResponse) {
        api.token = res.token
        // A different person on this phone: drop the previous cache.
        if (account?.id != res.user.id) {
            moments = emptyList(); friends = emptyList(); friendPosts = emptyList(); blocked = emptyList(); outbox = emptyList()
        }
        account = res.user
        path.clear()
        authGeneration++
        save()
    }

    suspend fun logout() {
        runCatching { api.send("POST", "auth/logout") }
        clearSession()
    }

    private fun clearSession() {
        api.token = null
        NotificationService.cancelAll(ctx)
        NotificationService.stopFriendWatch(ctx)
        account = null
        moments = emptyList(); friends = emptyList(); friendPosts = emptyList(); blocked = emptyList()
        clearOutbox()
        path.clear()
        authGeneration++
        cacheFile.delete()
        ctx.getSharedPreferences("seeli.prefs", Context.MODE_PRIVATE).edit().remove("seeli.feedSeen").apply()
        updateWidgets()
    }

    /** Deletes the account (the server also revokes any linked Apple ID it holds a token for). */
    suspend fun deleteAccount(): Boolean {
        try {
            api.send("DELETE", "me")
        } catch (e: ApiError) {
            // An Apple-linked account created on iPhone: Apple must confirm the deletion there.
            lastError = if (e.code == "apple_reauth_required") t("error.apple_reauth_required").let {
                if (it == "error.apple_reauth_required") t("error.generic") else it
            } else e.description
            return false
        }
        clearSession()
        return true
    }

    suspend fun updateProfile(name: String, bio: String, handle: String? = null): Boolean {
        val body = mutableMapOf<String, Any?>("name" to name, "bio" to bio)
        if (handle != null) body["handle"] = handle
        val user = attempt { api.request<UserAccount>("PATCH", "me", body) } ?: return false
        account = user
        save()
        return true
    }

    suspend fun rotateInviteCode() {
        attempt { api.request<UserAccount>("POST", "me/invite/rotate") }?.let { account = it; save() }
    }

    val myInvite get() = FriendInvite(account?.inviteCode ?: "", account?.name ?: "")

    // ---- Sync

    /** Pull everything from the server and flush the outbox. */
    suspend fun refresh() {
        if (!isLoggedIn || isSyncing) return
        isSyncing = true
        try {
            flushOutbox()
            val meReq = scope.async { runCatching { api.request<UserAccount>("GET", "me") }.getOrNull() }
            val momentsReq = scope.async { runCatching { api.request<List<Moment>>("GET", "moments") }.getOrNull() }
            val friendsReq = scope.async { runCatching { api.request<List<Friend>>("GET", "friends") }.getOrNull() }
            val feedReq = scope.async { runCatching { api.request<List<FriendPost>>("GET", "feed") }.getOrNull() }
            val blocksReq = scope.async { runCatching { api.request<List<PublicUser>>("GET", "blocks") }.getOrNull() }
            val healthReq = scope.async { runCatching { api.request<Health>("GET", "health") }.getOrNull() }
            healthReq.await()?.let {
                serverAllowsDemo = it.allowDemo ?: false
                if (!serverAllowsDemo) demoMode = false
            }
            val me = meReq.await(); val ms = momentsReq.await(); val fs = friendsReq.await()
            val feed = feedReq.await(); val bl = blocksReq.await()
            if (!isLoggedIn) return
            me?.let { account = it }
            ms?.let { moments = it; rescheduleNotifications() }
            fs?.let { friends = it }
            feed?.let { list ->
                // The server is the source of truth for "viewed" (it can be reset there).
                friendPosts = list
                markFeedSeen(list)
            }
            bl?.let { blocked = it }
            refreshClock()
            save()
            enableFriendPush()
        } finally {
            isSyncing = false
        }
    }

    /** Pull-to-refresh: wait for a sync already running, then fetch again (otherwise the pull did nothing). */
    suspend fun pullRefresh() {
        while (isSyncing) kotlinx.coroutines.delay(150)
        refresh()
    }

    /** Posts already visible in the app should not be announced again by the background check. */
    private fun markFeedSeen(list: List<FriendPost>) {
        val prefs = ctx.getSharedPreferences("seeli.prefs", Context.MODE_PRIVATE)
        val newest = list.maxOfOrNull { it.createdAt.toEpochMilli() } ?: 0L
        prefs.edit().putLong("seeli.feedSeen", maxOf(newest, prefs.getLong("seeli.feedSeen", 0L))).apply()
    }

    private fun rescheduleNotifications() {
        if (!notificationsEnabled) return
        val n = Instant.now()
        moments.filter { it.reunitedAt == null && it.revealAt > n }
            .forEach { NotificationService.scheduleReunion(ctx, it.id, it.revealAt) }
    }

    // ---- Push-like notifications for friends' posts

    /** Asks for permission once there is someone to hear from, then starts the feed check. */
    fun enableFriendPush() {
        if (!isLoggedIn || !friendPushEnabled || friends.isEmpty()) return
        if (!NotificationService.hasPermission(ctx)) { permissionRequest = "notifications"; return }
        NotificationService.startFriendWatch(ctx)
    }

    fun setFriendPush(on: Boolean) {
        friendPushEnabled = on
        save()
        if (on) enableFriendPush() else NotificationService.stopFriendWatch(ctx)
    }

    // ---- Moments

    /** Server moments plus photos still waiting in the outbox. */
    val allMoments: List<Moment>
        get() {
            val pending = outbox.map { p ->
                Moment(p.id, p.note, p.placeName, p.audience, p.createdAt, p.createdAt.plus(30, ChronoUnit.DAYS), isPending = true)
            }
            return (pending + moments).sortedByDescending { it.createdAt }
        }

    fun startDraft(imageData: ByteArray?) {
        draft = Draft(imageData = imageData, capturedAt = Instant.now(), audience = defaultAudience)
    }

    /** Save to the outbox first (so nothing is lost offline), then upload. */
    fun commitDraft() {
        val data = draft.imageData ?: return
        val p = PendingUpload(
            id = "local-${UUID.randomUUID()}",
            note = draft.note.trim(),
            placeName = if (draft.recordPlace) draft.placeName else null,
            audience = draft.audience,
            createdAt = draft.capturedAt,
            demo = demoMode && serverAllowsDemo,
        )
        runCatching { File(outboxDir, "${p.id}.jpg").writeBytes(PhotoPrep.jpeg(data)) }
        outbox = outbox + p
        draft = Draft()
        save()
        scope.launch { flushOutbox() }
        if (notificationsEnabled && !NotificationService.hasPermission(ctx)) permissionRequest = "notifications"
    }

    private var flushing = false

    suspend fun flushOutbox() {
        if (!isLoggedIn || flushing) return
        flushing = true
        try {
            for (p in outbox.toList()) {
                val file = File(outboxDir, "${p.id}.jpg")
                if (!file.exists()) { outbox = outbox.filterNot { it.id == p.id }; continue }
                val moment = try { api.uploadMoment(p, file.readBytes()) } catch (_: Exception) { break } // offline: later
                file.delete()
                outbox = outbox.filterNot { it.id == p.id }
                moments = listOf(moment) + moments
                if (notificationsEnabled) NotificationService.scheduleReunion(ctx, moment.id, moment.revealAt)
                save()
            }
        } finally {
            flushing = false
        }
    }

    private fun clearOutbox() {
        outbox = emptyList()
        outboxDir.deleteRecursively()
    }

    val dueMoments: List<Moment>
        get() = moments.filter { it.reunitedAt == null && it.revealAt <= now }.sortedBy { it.createdAt }

    val sleepingCount: Int get() = allMoments.count { it.reunitedAt == null && it.revealAt > now }

    fun moment(id: String) = allMoments.firstOrNull { it.id == id }

    private fun replace(m: Moment) {
        moments = moments.map { if (it.id == m.id) m else it }
        save()
    }

    /** Marks the reunion on the server and returns the fresh moment (with a new photo URL). */
    suspend fun reunite(id: String): Moment? {
        val m = attempt { api.request<Moment>("POST", "moments/$id/reunite") } ?: return null
        replace(m)
        return m
    }

    suspend fun saveReflection(id: String, text: String): Boolean {
        val m = attempt { api.request<Moment>("PATCH", "moments/$id", mapOf("reflection" to text.trim())) } ?: return false
        replace(m)
        return true
    }

    suspend fun deleteMoment(id: String) {
        if (id.startsWith("local-")) {
            File(outboxDir, "$id.jpg").delete()
            outbox = outbox.filterNot { it.id == id }
            save()
            return
        }
        attempt { api.send("DELETE", "moments/$id") } ?: return
        NotificationService.cancel(ctx, id)
        moments = moments.filterNot { it.id == id }
        save()
    }

    /** The owner answers a friend's perspective on their photo. */
    suspend fun replyToComment(momentID: String, commentID: String, text: String): CommentReply? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        return attempt { api.request<CommentReply>("PUT", "moments/$momentID/comments/$commentID/reply", mapOf("text" to clean)) }
    }

    suspend fun comments(id: String): List<Comment> = attempt { api.request<List<Comment>>("GET", "moments/$id/comments") } ?: emptyList()

    fun refreshClock() { now = Instant.now() }

    /** seeli:// deep links from widgets, notifications and QR codes. */
    fun handle(uri: Uri) {
        if (uri.scheme != "seeli") return
        when (uri.host) {
            "camera" -> setPath(Route.Camera)
            "memories" -> setPath(Route.Memories)
            "connect" -> setPath(Route.Connect)
            "comments" -> {
                pendingCommentsMoment = uri.lastPathSegment
                setPath(Route.Memories)
                scope.launch { refresh() }
            }
            "reunion" -> {
                refreshClock()
                dueMoments.firstOrNull()?.let { setPath(Route.Reunion(it.id)) } ?: path.clear()
            }
            "home" -> path.clear()
            else -> FriendInvite.parse(uri.toString())?.let { pendingInvite = it }
        }
    }

    fun setPath(vararg routes: Route) { path.clear(); path.addAll(routes) }
    fun push(route: Route) { path.add(route) }
    fun pop() { if (path.isNotEmpty()) path.removeAt(path.lastIndex) }

    // ---- Friends

    @Serializable
    data class InvitePreview(val user: PublicUser, val isSelf: Boolean, val isFriend: Boolean)

    fun friend(id: String) = friends.firstOrNull { it.id == id }

    val visiblePosts get() = friendPosts.sortedByDescending { it.createdAt }

    suspend fun preview(invite: FriendInvite): InvitePreview = api.request("GET", "invites/${invite.code}")

    suspend fun addFriend(invite: FriendInvite): Friend? {
        val f = attempt { api.request<Friend>("POST", "friends", mapOf("code" to invite.code)) } ?: return null
        friends = listOf(f) + friends.filterNot { it.id == f.id }
        save()
        scope.launch { refresh() }
        return f
    }

    suspend fun updateMemo(id: String, memo: String) {
        if (friends.none { it.id == id }) return
        friends = friends.map { if (it.id == id) it.copy(memo = memo) else it }
        save()
        attempt { api.request<Friend>("PATCH", "friends/$id", mapOf("memo" to memo)) }
    }

    suspend fun removeFriend(id: String) {
        attempt { api.send("DELETE", "friends/$id") } ?: return
        friends = friends.filterNot { it.id == id }
        friendPosts = friendPosts.filterNot { it.author.id == id }
        save()
    }

    suspend fun block(id: String) {
        attempt { api.send("POST", "friends/$id/block") } ?: return
        friend(id)?.let { blocked = blocked + PublicUser(it.id, it.name, it.handle) }
        friends = friends.filterNot { it.id == id }
        friendPosts = friendPosts.filterNot { it.author.id == id }
        save()
    }

    suspend fun unblock(id: String) {
        attempt { api.send("DELETE", "blocks/$id") } ?: return
        blocked = blocked.filterNot { it.id == id }
        save()
    }

    suspend fun report(id: String): Boolean =
        attempt { api.send("POST", "reports", mapOf("userId" to id, "reason" to "user_report")) } != null

    @Serializable
    private data class ViewResponse(val photoUrl: String, val viewedAt: Date)

    /** Opens a friend's photo. The server records the view, so it works only once. */
    private suspend fun openOncePhoto(postID: String): String? = try {
        val r = api.request<ViewResponse>("POST", "moments/$postID/view")
        lastError = null
        markViewed(postID, r.viewedAt)
        r.photoUrl
    } catch (e: ApiError) {
        if (e.code == "already_viewed") markViewed(postID, Instant.now())
        lastError = e.description
        null
    }

    /** Fetches a friend's photo once, straight into memory (no HTTP cache, no disk). */
    suspend fun loadOncePhoto(postID: String) {
        val url = openOncePhoto(postID) ?: return
        repeat(3) { attempt ->
            val bytes = api.fetchBytes(url, api.ephemeral)
            val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            if (bmp != null) { onceImages[postID] = bmp; return }
            if (attempt < 2) delay(1000)
        }
    }

    fun forgetOncePhotos() = onceImages.clear()

    private fun markViewed(postID: String, at: Instant) {
        if (friendPosts.none { it.id == postID && it.viewedAt == null }) return
        friendPosts = friendPosts.map { if (it.id == postID) it.copy(viewedAt = at) else it }
        save()
    }

    suspend fun sendPerspective(postID: String, text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        attempt { api.send("PUT", "moments/$postID/comment", mapOf("text" to clean)) } ?: return false
        friendPosts = friendPosts.map { if (it.id == postID) it.copy(myComment = clean) else it }
        save()
        return true
    }
}
