package com.a02.draw.feature.home.common.ads

import androidx.fragment.app.Fragment
import com.a02.draw.core.ui.ads.AppAdsController
import com.a02.draw.core.ui.ads.RewardContentKey
import com.a02.draw.core.ui.ads.RewardUnlockResult
import com.a02.draw.feature.home.R
import com.google.android.material.snackbar.Snackbar

fun Fragment.requestVipUnlock(
    ads: AppAdsController,
    content: RewardContentKey,
    itemName: CharSequence,
    onUnlocked: () -> Unit,
) {
    ads.requestRewardedUnlock(
        activity = requireActivity(),
        lifecycleOwner = viewLifecycleOwner,
        content = content,
        itemName = itemName,
    ) { result ->
        when (result) {
            RewardUnlockResult.GRANTED -> onUnlocked()
            RewardUnlockResult.NOT_READY -> view?.let { root ->
                Snackbar.make(
                    root,
                    R.string.reward_ad_unavailable,
                    Snackbar.LENGTH_SHORT,
                ).show()
            }

            RewardUnlockResult.NOT_COMPLETED -> view?.let { root ->
                Snackbar.make(
                    root,
                    R.string.reward_ad_not_completed,
                    Snackbar.LENGTH_SHORT,
                ).show()
            }

            RewardUnlockResult.DECLINED,
            RewardUnlockResult.BUSY -> Unit
        }
    }
}

fun Fragment.runAdNavigation(
    ads: AppAdsController,
    isEligible: Boolean = true,
    action: () -> Unit,
) {
    ads.runNavigationInterstitial(requireActivity(), isEligible, action)
}
