package com.tunnaduong.seeli

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.tunnaduong.seeli.core.ApiClient
import com.tunnaduong.seeli.core.AppStore
import com.tunnaduong.seeli.core.L10n
import com.tunnaduong.seeli.core.NotificationService
import com.tunnaduong.seeli.core.Route
import com.tunnaduong.seeli.ui.Seeli
import com.tunnaduong.seeli.ui.SeeliTheme
import com.tunnaduong.seeli.ui.screens.AddFriendSheet
import com.tunnaduong.seeli.ui.screens.AudienceScreen
import com.tunnaduong.seeli.ui.screens.AuthFlow
import com.tunnaduong.seeli.ui.screens.CameraScreen
import com.tunnaduong.seeli.ui.screens.ConnectScreen
import com.tunnaduong.seeli.ui.screens.FriendProfileScreen
import com.tunnaduong.seeli.ui.screens.FriendRequestsScreen
import com.tunnaduong.seeli.ui.screens.FriendsScreen
import com.tunnaduong.seeli.ui.screens.HomeScreen
import com.tunnaduong.seeli.ui.screens.MemoriesScreen
import com.tunnaduong.seeli.ui.screens.MyQRScreen
import com.tunnaduong.seeli.ui.screens.PostScreen
import com.tunnaduong.seeli.ui.screens.PostedScreen
import com.tunnaduong.seeli.ui.screens.PrivacyScreen
import com.tunnaduong.seeli.ui.screens.ProfileScreen
import com.tunnaduong.seeli.ui.screens.QRScanScreen
import com.tunnaduong.seeli.ui.screens.ReunionScreen
import com.tunnaduong.seeli.ui.screens.SettingsScreen
import com.tunnaduong.seeli.ui.screens.VerifiedScreen
import com.tunnaduong.seeli.ui.screens.WhatsNew
import com.tunnaduong.seeli.ui.screens.WhatsNewSheet
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

class SeeliApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        L10n.init(this)
        ApiClient.init(this)
        AppStore.init(this)
        NotificationService.createChannels(this)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        handleIntent(intent)
        setContent {
            SeeliTheme {
                // Status / navigation bar icons follow the app's light or dark look.
                LaunchedEffect(Seeli.dark) {
                    val style = if (Seeli.dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                Root()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (AppStore.isLoggedIn) AppStore.handle(uri)
    }

    override fun onStart() {
        super.onStart()
        AppStore.refreshClock()
        lifecycleScope.launch { AppStore.refresh() }
    }

    override fun onStop() {
        super.onStop()
        AppStore.updateWidgets()
        AppStore.forgetOncePhotos()
    }
}

@Composable
private fun Root() {
    // Re-key the whole tree on language change so every t() re-reads.
    key(L10n.language) {
        Box(Modifier.fillMaxSize().background(Seeli.BG)) {
            Crossfade(AppStore.isLoggedIn && !AppStore.welcomePending, animationSpec = tween(350), label = "root") { inside ->
                if (inside) MainFlow() else AuthFlow()
            }
        }
    }
    PermissionPrompt()
}

/** Runtime permission requests the store asks for (notifications). */
@Composable
private fun PermissionPrompt() {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) AppStore.enableFriendPush()
    }
    LaunchedEffect(AppStore.permissionRequest) {
        if (AppStore.permissionRequest == "notifications") {
            AppStore.permissionRequest = null
            if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainFlow() {
    val top = AppStore.path.lastOrNull()
    BackHandler(enabled = top != null && top != Route.Posted) { AppStore.pop() }
    BackHandler(enabled = top == Route.Posted) { AppStore.path.clear() }

    AnimatedContent(
        targetState = AppStore.path.size to top,
        transitionSpec = {
            val forward = targetState.first > initialState.first
            if (forward) slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(260)) togetherWith fadeOut(tween(180))
            else fadeIn(tween(220)) togetherWith slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(220))
        },
        contentKey = { it.second },
        label = "nav",
    ) { (_, route) ->
        when (route) {
            null -> HomeScreen()
            Route.Camera -> CameraScreen()
            Route.Post -> PostScreen()
            Route.AudienceRoute -> AudienceScreen()
            Route.Posted -> PostedScreen()
            Route.Connect -> ConnectScreen()
            Route.Memories -> MemoriesScreen()
            Route.Profile -> ProfileScreen()
            Route.Friends -> FriendsScreen()
            is Route.FriendProfile -> FriendProfileScreen(route.id)
            Route.Privacy -> PrivacyScreen()
            Route.Settings -> SettingsScreen()
            Route.Verified -> VerifiedScreen()
            Route.Requests -> FriendRequestsScreen()
            Route.Scan -> QRScanScreen()
            Route.MyQR -> MyQRScreen()
            is Route.Reunion -> ReunionScreen(route.id)
            is Route.MomentDetail -> ReunionScreen(route.id)
        }
    }

    // "What's new" once per version, a moment after opening (not over a pending invite).
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var showWhatsNew by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (WhatsNew.isPending(ctx)) {
            kotlinx.coroutines.delay(600)
            if (AppStore.pendingInvite == null) showWhatsNew = true
        }
    }
    if (showWhatsNew) {
        ModalBottomSheet({ WhatsNew.markSeen(ctx); showWhatsNew = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Seeli.BG) {
            WhatsNewSheet { WhatsNew.markSeen(ctx); showWhatsNew = false }
        }
    }

    AppStore.pendingInvite?.let { invite ->
        ModalBottomSheet({ AppStore.pendingInvite = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Seeli.BG) {
            AddFriendSheet(invite) { AppStore.pendingInvite = null }
        }
    }
}
