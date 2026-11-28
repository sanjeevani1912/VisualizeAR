package com.example.dhruvar.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dhruvar.BuildConfig
import com.example.dhruvar.data.model.LayoutJsonSerializer
import com.example.dhruvar.data.remote.SentPlan
import com.example.dhruvar.data.remote.SentPlanStore
import com.example.dhruvar.data.remote.ServerClient
import com.example.dhruvar.data.remote.ServerException
import com.example.dhruvar.data.remote.ServerUrl
import com.example.dhruvar.data.remote.ServerUrlCheck
import com.example.dhruvar.data.remote.SessionStore
import com.example.dhruvar.data.remote.SignedInUser
import com.example.dhruvar.data.repository.DefaultLayoutRepositoryProvider
import com.example.dhruvar.data.repository.UserScopedLayoutRepository
import com.example.dhruvar.domain.model.Layout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(
    val serverUrl: String = "",
    val user: SignedInUser? = null,
    val isSigningIn: Boolean = false,
    val signInError: String? = null,
    /** Plans sent from this phone, keyed by local layout id. */
    val sent: Map<String, SentPlan> = emptyMap(),
    val sendingLayoutId: String? = null,
    val isRefreshing: Boolean = false,
    /** One-shot message for a snackbar. */
    val message: String? = null
) {
    val isSignedIn: Boolean get() = user != null
}

/**
 * Sign-in to the dashboard server and sending plans for approval.
 *
 * The planning, saving and AR flows never depend on this: the app stays fully usable offline
 * and signed out. The server is contacted only when the user signs in, sends a plan, or
 * refreshes statuses.
 */
class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val session = SessionStore(application)
    private val initialUser: SignedInUser? = if (session.token() != null) session.user() else null

    /** Sent-plan records of the signed-in user only (one file per user id). */
    private var sentStore = SentPlanStore(application, initialUser?.id)

    /** Per-user plan folders; null only in tests that install a different repository. */
    private val plans: UserScopedLayoutRepository? =
        DefaultLayoutRepositoryProvider.getRepository() as? UserScopedLayoutRepository

    private val _uiState = MutableStateFlow(
        AccountUiState(
            serverUrl = session.serverUrl.ifBlank { BuildConfig.DEFAULT_SERVER_URL },
            user = initialUser,
            sent = sentStore.all()
        )
    )
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    init {
        plans?.setUser(initialUser?.id)
    }

    /** Points plan folders and sent records at [user] (null = nobody signed in). */
    private fun switchUser(user: SignedInUser?) {
        plans?.setUser(user?.id)
        sentStore = SentPlanStore(getApplication(), user?.id)
    }

    /** A plan the user tried to send while signed out; it goes out right after sign-in. */
    private var pendingSend: Pair<Layout, String>? = null

    fun signIn(rawUrl: String, username: String, password: String) {
        if (_uiState.value.isSigningIn) return
        val base = when (val check = ServerUrl.normalize(rawUrl, allowCleartext = BuildConfig.DEBUG)) {
            is ServerUrlCheck.Ok -> check.baseUrl
            is ServerUrlCheck.Bad -> {
                _uiState.update { it.copy(signInError = check.reason) }
                return
            }
        }
        if (username.isBlank() || password.isEmpty()) {
            _uiState.update { it.copy(signInError = "Enter your username and password") }
            return
        }
        _uiState.update { it.copy(isSigningIn = true, signInError = null) }
        viewModelScope.launch {
            try {
                val result = ServerClient.signIn(base, username.trim(), password)
                session.serverUrl = base
                ServerUrl.mapBaseFor(base)?.let { map ->
                    session.mapUrl = map
                    com.example.dhruvar.data.map.MapBasemapConfig.runtimeMapBase = map
                }
                session.saveSession(result)
                switchUser(result.user)
                // A plan saved on this phone without an account is never sent as it is: it is
                // first copied into the user's own plans, and the copy is sent.
                val pending = pendingSend?.let { (layout, note) ->
                    pendingSend = null
                    val target = if (plans != null && plans.isViewOnly(layout.id)) {
                        plans.copyToMine(layout.id)
                    } else {
                        layout
                    }
                    target?.let { it to note }
                }
                _uiState.update {
                    it.copy(
                        serverUrl = base,
                        user = result.user,
                        sent = sentStore.all(),
                        isSigningIn = false,
                        signInError = null
                    )
                }
                refreshStatuses()
                pending?.let { (layout, note) -> send(layout, note) }
            } catch (e: ServerException) {
                _uiState.update { it.copy(isSigningIn = false, signInError = e.message) }
            }
        }
    }

    fun signOut() {
        val base = session.serverUrl
        val token = session.token()
        session.clearSession()
        pendingSend = null
        switchUser(null)
        _uiState.update { it.copy(user = null, sent = emptyMap(), sendingLayoutId = null, message = "Signed out") }
        if (token != null && base.isNotBlank()) {
            viewModelScope.launch { ServerClient.signOut(base, token) }
        }
    }

    /** Called from the Home screen when the user wants to send but is not signed in yet. */
    fun sendAfterSignIn(layout: Layout, note: String) {
        pendingSend = layout to note
    }

    fun clearSignInError() {
        _uiState.update { it.copy(signInError = null) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun send(layout: Layout, note: String) {
        val base = session.serverUrl
        val token = session.token()
        if (token == null || base.isBlank()) {
            _uiState.update { it.copy(user = null, message = "Sign in to send plans") }
            return
        }
        if (plans != null && plans.isViewOnly(layout.id)) {
            _uiState.update { it.copy(message = "Make a copy in your plans before sending") }
            return
        }
        if (layout.regionBounds == null || !layout.anchor.hasGeographicFix) {
            _uiState.update { it.copy(message = "Set a region and an origin before sending this plan") }
            return
        }
        if (_uiState.value.sendingLayoutId != null) return
        _uiState.update { it.copy(sendingLayoutId = layout.id) }
        val store = sentStore
        viewModelScope.launch {
            try {
                val json = LayoutJsonSerializer.serialize(layout)
                val existing = _uiState.value.sent[layout.id]
                val now = System.currentTimeMillis()
                val record: SentPlan
                val text: String
                if (existing != null && existing.canResubmit) {
                    ServerClient.submitVersion(base, token, existing.planId, json, note)
                    record = existing.copy(version = existing.version + 1, status = "SUBMITTED", sentAtEpochMs = now, feedback = emptyList())
                    text = "Resubmitted ${existing.code} as version ${record.version}"
                } else if (existing != null) {
                    _uiState.update { it.copy(sendingLayoutId = null, message = "${existing.code} was already sent") }
                    return@launch
                } else {
                    val created = ServerClient.submitPlan(base, token, json, note)
                    record = SentPlan(created.planId, created.code, 1, "SUBMITTED", now)
                    text = "Sent for approval as ${created.code}"
                }
                store.put(layout.id, record)
                // Signed out (or another user signed in) while sending: leave the new list alone.
                if (store !== sentStore) return@launch
                _uiState.update {
                    it.copy(sent = it.sent + (layout.id to record), sendingLayoutId = null, message = text)
                }
            } catch (e: ServerException) {
                handleFailure(e)
            }
        }
    }

    /** Pulls the latest decision for every plan this account has sent. */
    fun refreshStatuses() {
        val base = session.serverUrl
        val token = session.token() ?: return
        if (base.isBlank() || _uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        val store = sentStore
        viewModelScope.launch {
            try {
                val remote = ServerClient.fetchPlanStatuses(base, token)
                val local = _uiState.value.sent.toMutableMap()
                for (plan in remote) {
                    val layoutId = plan.layoutId ?: continue
                    val before = local[layoutId]
                    val updated = SentPlan(
                        planId = plan.planId,
                        code = plan.code,
                        version = plan.latestVersion,
                        status = plan.status,
                        sentAtEpochMs = before?.sentAtEpochMs ?: 0L,
                        feedback = plan.feedback
                    )
                    if (before != updated) {
                        store.put(layoutId, updated)
                        local[layoutId] = updated
                    }
                }
                if (store !== sentStore) {
                    _uiState.update { it.copy(isRefreshing = false) }
                    return@launch
                }
                _uiState.update { it.copy(sent = local, isRefreshing = false) }
            } catch (e: ServerException) {
                _uiState.update { it.copy(isRefreshing = false) }
                if (e.isSessionExpired) handleFailure(e)
                // Other failures (for example no signal) are silent: the last known status stays.
            }
        }
    }

    private fun handleFailure(e: ServerException) {
        if (e.isSessionExpired) {
            session.clearSession()
            switchUser(null)
            _uiState.update {
                it.copy(user = null, sent = emptyMap(), sendingLayoutId = null, message = "Your session expired. Sign in again.")
            }
        } else {
            _uiState.update { it.copy(sendingLayoutId = null, message = e.message) }
        }
    }
}
