package com.lustyflix.streamverse.ui.home

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import com.lustyflix.streamverse.LoadResponse
import com.lustyflix.streamverse.databinding.HomeScrollViewBinding
import com.lustyflix.streamverse.databinding.HomeScrollViewTvBinding
import com.lustyflix.streamverse.ui.NoStateAdapter
import com.lustyflix.streamverse.ui.ViewHolderState
import com.lustyflix.streamverse.ui.settings.Globals.EMULATOR
import com.lustyflix.streamverse.ui.settings.Globals.TV
import com.lustyflix.streamverse.ui.settings.Globals.isLayout
import com.lustyflix.streamverse.utils.UIHelper.setImage

class HomeScrollAdapter(
    fragment: Fragment
) : NoStateAdapter<LoadResponse>(fragment) {
    var hasMoreItems: Boolean = false

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        val inflater = LayoutInflater.from(parent.context)
        val binding = if (isLayout(TV or EMULATOR)) {
            HomeScrollViewTvBinding.inflate(inflater, parent, false)
        } else {
            HomeScrollViewBinding.inflate(inflater, parent, false)
        }

        return ViewHolderState(binding)
    }

    override fun onBindContent(
        holder: ViewHolderState<Any>,
        item: LoadResponse,
        position: Int,
    ) {
        val binding = holder.view
        val itemView = holder.itemView
        val isHorizontal =
            binding is HomeScrollViewTvBinding || itemView.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val posterUrl =
            if (isHorizontal) item.backgroundPosterUrl ?: item.posterUrl else item.posterUrl
                ?: item.backgroundPosterUrl

        when (binding) {
            is HomeScrollViewBinding -> {
                binding.homeScrollPreview.setImage(posterUrl)
                binding.homeScrollPreviewTags.apply {
                    text = item.tags?.joinToString(" • ") ?: ""
                    isGone = item.tags.isNullOrEmpty()
                    maxLines = 2
                }
                binding.homeScrollPreviewTitle.text = item.name
            }

            is HomeScrollViewTvBinding -> {
                binding.homeScrollPreview.setImage(posterUrl)
            }
        }
    }
}