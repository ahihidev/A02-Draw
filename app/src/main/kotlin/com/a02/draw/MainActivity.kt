package com.a02.draw

import android.os.Bundle
import com.a02.draw.core.ui.base.BaseActivity
import com.a02.draw.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>(ActivityMainBinding::inflate) {
    override fun setupViews(savedInstanceState: Bundle?) = Unit
}
