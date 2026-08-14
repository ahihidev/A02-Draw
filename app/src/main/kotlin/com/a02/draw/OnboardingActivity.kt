package com.a02.draw

import android.os.Bundle
import com.a02.draw.core.ui.base.BaseActivity
import com.a02.draw.databinding.ActivityOnboardingBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnboardingActivity : BaseActivity<ActivityOnboardingBinding>(
    ActivityOnboardingBinding::inflate,
) {
    override fun setupViews(savedInstanceState: Bundle?) = Unit

    override fun observeData() = Unit
}
