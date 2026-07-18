package com.a02.draw.core.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

abstract class BaseFragment<VB : ViewBinding>(
    private val inflate: (LayoutInflater, ViewGroup?, Boolean) -> VB,
) : Fragment() {
    private var internalBinding: VB? = null
    protected val binding: VB
        get() = requireNotNull(internalBinding) {
            "Binding is only available between onCreateView() and onDestroyView()."
        }

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflate(inflater, container, false).also { internalBinding = it }.root

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(savedInstanceState)
        setupListeners()
        observeData()
    }

    protected abstract fun setupViews(savedInstanceState: Bundle?)

    protected open fun setupListeners() = Unit

    protected open fun observeData() = Unit

    protected fun collectWhenStarted(block: suspend CoroutineScope.() -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED, block)
        }
    }

    override fun onDestroyView() {
        internalBinding = null
        super.onDestroyView()
    }
}
