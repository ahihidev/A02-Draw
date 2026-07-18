package com.a02.draw.core.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

abstract class BaseBottomSheetFragment<VB : ViewBinding>(
    private val inflate: (LayoutInflater, ViewGroup?, Boolean) -> VB,
) : BottomSheetDialogFragment() {
    private var internalBinding: VB? = null
    protected val binding: VB
        get() = requireNotNull(internalBinding) { "Bottom-sheet binding is no longer available." }

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
