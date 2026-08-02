package com.a02.draw.feature.home.screen.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.applyStatusBarPadding
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.component.TrendingSearchAdapter
import com.a02.draw.feature.home.databinding.ScreenSearchBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : BaseFragment<ScreenSearchBinding>(ScreenSearchBinding::inflate) {
    private val viewModel: SearchViewModel by viewModels()
    private var rendering = false
    private val adapter by lazy {
        TrendingSearchAdapter { viewModel.onAction(SearchAction.SelectTrending(it)) }
    }

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.toolbar.applyStatusBarPadding()
        binding.contentList.layoutManager = LinearLayoutManager(requireContext())
        binding.contentList.adapter = adapter
        binding.backButton.setDebouncedClickListener { viewModel.onAction(SearchAction.Back) }
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                value: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun afterTextChanged(value: Editable?) = Unit
            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
                if (!rendering) viewModel.onAction(
                    SearchAction.QueryChanged(
                        value?.toString().orEmpty()
                    )
                )
            }
        })
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.onAction(SearchAction.Submit)
                true
            } else false
        }
    }

    override fun observeData() {
        collectWhenStarted {
            launch {
                viewModel.state.collect { state ->
                    if (binding.searchInput.text.toString() != state.query) {
                        rendering = true
                        binding.searchInput.setText(state.query)
                        binding.searchInput.setSelection(state.query.length)
                        rendering = false
                    }
                    val showSkeleton = state.isLoading && state.trending.isEmpty()
                    adapter.submitList(state.trending)
                    binding.sectionTitle.setText(R.string.trending)
                    binding.loadingSkeleton.isVisible = showSkeleton
                    binding.contentList.isVisible = !showSkeleton
                    binding.emptyMessage.isVisible = false
                }
            }
            launch {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is SearchEffect.OpenResults -> findNavController().navigate(
                            R.id.searchResultsFragment,
                            Bundle().apply { putString(ARG_QUERY, effect.query) },
                        )

                        SearchEffect.NavigateBack -> findNavController().navigateUp()
                    }
                }
            }
        }
    }

    private companion object {
        const val ARG_QUERY = "query"
    }
}
