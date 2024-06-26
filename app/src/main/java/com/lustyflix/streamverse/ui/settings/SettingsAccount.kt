package com.lustyflix.streamverse.ui.settings

import android.os.Bundle
import android.view.View
import android.view.View.*
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.RecyclerView
import com.lustyflix.streamverse.AcraApplication.Companion.openBrowser
import com.lustyflix.streamverse.CommonActivity.onDialogDismissedEvent
import com.lustyflix.streamverse.CommonActivity.showToast
import com.lustyflix.streamverse.R
import com.lustyflix.streamverse.databinding.AccountManagmentBinding
import com.lustyflix.streamverse.databinding.AccountSwitchBinding
import com.lustyflix.streamverse.databinding.AddAccountInputBinding
import com.lustyflix.streamverse.mvvm.logError
import com.lustyflix.streamverse.syncproviders.AccountManager
import com.lustyflix.streamverse.syncproviders.AccountManager.Companion.aniListApi
import com.lustyflix.streamverse.syncproviders.AccountManager.Companion.malApi
import com.lustyflix.streamverse.syncproviders.AccountManager.Companion.openSubtitlesApi
import com.lustyflix.streamverse.syncproviders.AccountManager.Companion.simklApi
import com.lustyflix.streamverse.syncproviders.AuthAPI
import com.lustyflix.streamverse.syncproviders.InAppAuthAPI
import com.lustyflix.streamverse.syncproviders.OAuth2API
import com.lustyflix.streamverse.ui.settings.Globals.EMULATOR
import com.lustyflix.streamverse.ui.settings.Globals.PHONE
import com.lustyflix.streamverse.ui.settings.Globals.TV
import com.lustyflix.streamverse.ui.settings.Globals.isLayout
import com.lustyflix.streamverse.ui.settings.SettingsFragment.Companion.getPref
import com.lustyflix.streamverse.ui.settings.SettingsFragment.Companion.setPaddingBottom
import com.lustyflix.streamverse.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import com.lustyflix.streamverse.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lustyflix.streamverse.utils.AppUtils.html
import com.lustyflix.streamverse.utils.BackupUtils
import com.lustyflix.streamverse.utils.BiometricAuthenticator
import com.lustyflix.streamverse.utils.BiometricAuthenticator.authCallback
import com.lustyflix.streamverse.utils.BiometricAuthenticator.biometricPrompt
import com.lustyflix.streamverse.utils.BiometricAuthenticator.deviceHasPasswordPinLock
import com.lustyflix.streamverse.utils.BiometricAuthenticator.isAuthEnabled
import com.lustyflix.streamverse.utils.BiometricAuthenticator.promptInfo
import com.lustyflix.streamverse.utils.BiometricAuthenticator.startBiometricAuthentication
import com.lustyflix.streamverse.utils.Coroutines.ioSafe
import com.lustyflix.streamverse.utils.SingleSelectionHelper.showBottomDialogText
import com.lustyflix.streamverse.utils.UIHelper.dismissSafe
import com.lustyflix.streamverse.utils.UIHelper.hideKeyboard
import com.lustyflix.streamverse.utils.UIHelper.setImage

class SettingsAccount : PreferenceFragmentCompat(), BiometricAuthenticator.BiometricAuthCallback {
    companion object {
        /** Used by nginx plugin too */
        fun showLoginInfo(
            activity: FragmentActivity?,
            api: AccountManager,
            info: AuthAPI.LoginInfo
        ) {
            if (activity == null) return
            val binding: AccountManagmentBinding =
                AccountManagmentBinding.inflate(activity.layoutInflater, null, false)
            val builder =
                AlertDialog.Builder(activity, R.style.AlertDialogCustom)
                    .setView(binding.root)
            val dialog = builder.show()

            binding.accountMainProfilePictureHolder.isVisible =
                binding.accountMainProfilePicture.setImage(info.profilePicture)

            binding.accountLogout.setOnClickListener {
                api.logOut()
                dialog.dismissSafe(activity)
            }

            (info.name ?: activity.getString(R.string.no_data)).let {
                dialog.findViewById<TextView>(R.id.account_name)?.text = it
            }

            binding.accountSite.text = api.name
            binding.accountSwitchAccount.setOnClickListener {
                dialog.dismissSafe(activity)
                showAccountSwitch(activity, api)
            }

            if (isLayout(TV or EMULATOR)) {
                binding.accountSwitchAccount.requestFocus()
            }
        }

        private fun showAccountSwitch(activity: FragmentActivity, api: AccountManager) {
            val accounts = api.getAccounts() ?: return
            val binding: AccountSwitchBinding =
                AccountSwitchBinding.inflate(activity.layoutInflater, null, false)

            val builder =
                AlertDialog.Builder(activity, R.style.AlertDialogCustom)
                    .setView(binding.root)
            val dialog = builder.show()

            binding.accountAdd.setOnClickListener {
                addAccount(activity, api)
                dialog?.dismissSafe(activity)
            }

            val ogIndex = api.accountIndex

            val items = ArrayList<AuthAPI.LoginInfo>()

            for (index in accounts) {
                api.accountIndex = index
                val accountInfo = api.loginInfo()
                if (accountInfo != null) {
                    items.add(accountInfo)
                }
            }
            api.accountIndex = ogIndex
            val adapter = AccountAdapter(items) {
                dialog?.dismissSafe(activity)
                api.changeAccount(it.card.accountIndex)
            }
            val list = dialog.findViewById<RecyclerView>(R.id.account_list)
            list?.adapter = adapter
        }

        @UiThread
        fun addAccount(activity: FragmentActivity?, api: AccountManager) {
            try {
                when (api) {
                    is OAuth2API -> {
                        api.authenticate(activity)
                    }

                    is InAppAuthAPI -> {
                        if (activity == null) return
                        val binding: AddAccountInputBinding =
                            AddAccountInputBinding.inflate(activity.layoutInflater, null, false)
                        val builder =
                            AlertDialog.Builder(activity, R.style.AlertDialogCustom)
                                .setView(binding.root)
                        val dialog = builder.show()

                        val visibilityMap = listOf(
                            binding.loginEmailInput to api.requiresEmail,
                            binding.loginPasswordInput to api.requiresPassword,
                            binding.loginServerInput to api.requiresServer,
                            binding.loginUsernameInput to api.requiresUsername
                        )

                        if (isLayout(TV or EMULATOR)) {
                            visibilityMap.forEach { (input, isVisible) ->
                                input.isVisible = isVisible

                                // Band-aid for weird FireTV behavior causing crashes because keyboard covers the screen
                                input.setOnEditorActionListener { textView, actionId, _ ->
                                    if (actionId == EditorInfo.IME_ACTION_NEXT) {
                                        val view = textView.focusSearch(FOCUS_DOWN)
                                        return@setOnEditorActionListener view?.requestFocus(
                                            FOCUS_DOWN
                                        ) == true
                                    }
                                    return@setOnEditorActionListener true
                                }
                            }
                        } else {
                            visibilityMap.forEach { (input, isVisible) ->
                                input.isVisible = isVisible
                            }
                        }

                        binding.loginEmailInput.isVisible = api.requiresEmail
                        binding.loginPasswordInput.isVisible = api.requiresPassword
                        binding.loginServerInput.isVisible = api.requiresServer
                        binding.loginUsernameInput.isVisible = api.requiresUsername
                        binding.createAccount.isGone = api.createAccountUrl.isNullOrBlank()
                        binding.createAccount.setOnClickListener {
                            openBrowser(
                                api.createAccountUrl ?: return@setOnClickListener,
                                activity
                            )
                            dialog.dismissSafe()
                        }

                        val displayedItems = listOf(
                            binding.loginUsernameInput,
                            binding.loginEmailInput,
                            binding.loginServerInput,
                            binding.loginPasswordInput
                        ).filter { it.isVisible }

                        displayedItems.foldRight(displayedItems.firstOrNull()) { item, previous ->
                            item.id.let { previous?.nextFocusDownId = it }
                            previous?.id?.let { item.nextFocusUpId = it }
                            item
                        }

                        displayedItems.firstOrNull()?.let {
                            binding.createAccount.nextFocusDownId = it.id
                            it.nextFocusUpId = binding.createAccount.id
                        }
                        binding.applyBtt.id.let {
                            displayedItems.lastOrNull()?.nextFocusDownId = it
                        }

                        binding.text1.text = api.name

                        if (api.storesPasswordInPlainText) {
                            api.getLatestLoginData()?.let { data ->
                                binding.loginEmailInput.setText(data.email ?: "")
                                binding.loginServerInput.setText(data.server ?: "")
                                binding.loginUsernameInput.setText(data.username ?: "")
                                binding.loginPasswordInput.setText(data.password ?: "")
                            }
                        }

                        binding.applyBtt.setOnClickListener {
                            val loginData = InAppAuthAPI.LoginData(
                                username = if (api.requiresUsername) binding.loginUsernameInput.text?.toString() else null,
                                password = if (api.requiresPassword) binding.loginPasswordInput.text?.toString() else null,
                                email = if (api.requiresEmail) binding.loginEmailInput.text?.toString() else null,
                                server = if (api.requiresServer) binding.loginServerInput.text?.toString() else null,
                            )
                            ioSafe {
                                val isSuccessful = try {
                                    api.login(loginData)
                                } catch (e: Exception) {
                                    logError(e)
                                    false
                                }
                                activity.runOnUiThread {
                                    try {
                                        showToast(
                                            activity.getString(if (isSuccessful) R.string.authenticated_user else R.string.authenticated_user_fail)
                                                .format(
                                                    api.name
                                                )
                                        )
                                    } catch (e: Exception) {
                                        logError(e) // format might fail
                                    }
                                }
                            }
                            dialog.dismissSafe(activity)
                        }
                        binding.cancelBtt.setOnClickListener {
                            dialog.dismissSafe(activity)
                        }
                    }

                    else -> {
                        throw NotImplementedError("You are trying to add an account that has an unknown login method")
                    }
                }
            } catch (e: Exception) {
                logError(e)
            }
        }
    }

    private fun updateAuthPreference(enabled: Boolean) {
        val biometricKey = getString(R.string.biometric_key)

        PreferenceManager.getDefaultSharedPreferences(context ?: return).edit()
            .putBoolean(biometricKey, enabled).apply()
        findPreference<SwitchPreferenceCompat>(biometricKey)?.isChecked = enabled
    }

    override fun onAuthenticationError() {
        updateAuthPreference(!isAuthEnabled(context ?: return))
    }

    override fun onAuthenticationSuccess() {
        if (isAuthEnabled(context?: return)) {
            updateAuthPreference(true)
            BackupUtils.backup(activity)
            activity?.showBottomDialogText(
                getString(R.string.biometric_setting),
                getString(R.string.biometric_warning).html()
            ) { onDialogDismissedEvent }
        } else {
            updateAuthPreference(false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.category_account)
        setPaddingBottom()
        setToolBarScrollFlags()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings_account, rootKey)

        // hide preference on tvs and emulators
        getPref(R.string.biometric_key)?.isEnabled = isLayout(PHONE)

        getPref(R.string.biometric_key)?.setOnPreferenceClickListener {
            val ctx = context ?: return@setOnPreferenceClickListener false

            if (deviceHasPasswordPinLock(ctx)) {
                startBiometricAuthentication(
                    activity?: return@setOnPreferenceClickListener false,
                    R.string.biometric_authentication_title,
                    false
                    )
                promptInfo?.let {
                    authCallback = this
                    biometricPrompt?.authenticate(it)
                }
            }

            false
        }

        val syncApis =
            listOf(
                R.string.mal_key to malApi,
                R.string.anilist_key to aniListApi,
                R.string.simkl_key to simklApi,
                R.string.opensubtitles_key to openSubtitlesApi,
            )

        for ((key, api) in syncApis) {
            getPref(key)?.apply {
                title =
                    getString(R.string.login_format).format(api.name, getString(R.string.account))
                setOnPreferenceClickListener {
                    val info = api.loginInfo()
                    if (info != null) {
                        showLoginInfo(activity, api, info)
                    } else {
                        addAccount(activity, api)
                    }
                    return@setOnPreferenceClickListener true
                }
            }
        }
    }
}
