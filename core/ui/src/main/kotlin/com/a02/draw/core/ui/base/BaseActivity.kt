package com.a02.draw.core.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

abstract class BaseActivity<VB : ViewBinding>(
    private val inflate: (LayoutInflater) -> VB,
) : AppCompatActivity() {
    private var internalBinding: VB? = null
    protected val binding: VB
        get() = requireNotNull(internalBinding) { "Binding is only available after onCreate()." }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        internalBinding = inflate(layoutInflater)
        setContentView(binding.root)
        setupViews(savedInstanceState)
        setupListeners()
        observeData()
    }

    protected abstract fun setupViews(savedInstanceState: Bundle?)

    protected open fun setupListeners() = Unit

    protected open fun observeData() = Unit

    protected fun collectWhenStarted(block: suspend CoroutineScope.() -> Unit) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED, block)
        }
    }
}
