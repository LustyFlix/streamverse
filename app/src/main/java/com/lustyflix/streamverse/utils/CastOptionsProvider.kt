package com.lustyflix.streamverse.utils

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.MediaIntentReceiver
import com.google.android.gms.cast.framework.media.NotificationOptions
import com.lustyflix.streamverse.R
import com.lustyflix.streamverse.ui.ControllerActivity
import java.util.*

class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        val buttonActions = listOf(
            MediaIntentReceiver.ACTION_REWIND,
            MediaIntentReceiver.ACTION_TOGGLE_PLAYBACK,
            MediaIntentReceiver.ACTION_FORWARD,
            MediaIntentReceiver.ACTION_STOP_CASTING
        )

        val name = ControllerActivity::class.qualifiedName!!

        val compatButtonAction = intArrayOf(1, 3)
        val notificationOptions =
            NotificationOptions.Builder()
                .setTargetActivityClassName(name)
                .setActions(buttonActions, compatButtonAction)
                .setForward30DrawableResId(R.drawable.go_forward_30)
                .setRewind30DrawableResId(R.drawable.go_back_30)
                .setSkipStepMs(30000)
                .build()

        val mediaOptions = CastMediaOptions.Builder()
            .setNotificationOptions(notificationOptions)
            .setExpandedControllerActivityClassName(name)
            .build()

        return CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            //.setReceiverApplicationId("")
            //  C0868879 = SAMPLE, CHANGE TO A NICE ID at https://developers.google.com/cast/docs/registration
            .setStopReceiverApplicationWhenEndingSession(true)
            .setCastMediaOptions(mediaOptions)
            .build()
    }

    override fun getAdditionalSessionProviders(p0: Context): MutableList<SessionProvider> {
        return Collections.emptyList()
    }
}