package com.a02.draw.core.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.viewbinding.ViewBinding

abstract class BaseDialogFragment<VB : ViewBinding>(
    private val inflate: (LayoutInflater, ViewGroup?, Boolean) -> VB,
) : DialogFragment() {
    private var internalBinding: VB? = null
    protected val binding: VB
        get() = requireNotNull(internalBinding) { "Dialog view binding is no longer available." }

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflate(inflater, container, false).also { internalBinding = it }.root

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(savedInstanceState)
    }

    protected abstract fun setupViews(savedInstanceState: Bundle?)

    override fun onDestroyView() {
        internalBinding = null
        super.onDestroyView()
    }
}
