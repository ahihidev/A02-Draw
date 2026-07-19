package com.a02.draw

import android.os.Bundle
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.a02.draw.core.ui.base.BaseActivity
import com.a02.draw.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>(ActivityMainBinding::inflate) {
    override fun setupViews(savedInstanceState: Bundle?) {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
