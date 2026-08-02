package com.a02.draw.feature.home.screen.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.applyStatusBarPadding
import com.a02.draw.core.ui.extensions.setAdaptiveGridLayoutManager
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.image.HomeImageLoader
import com.a02.draw.feature.home.common.model.BottomDestination
import com.a02.draw.feature.home.common.model.DeviceImageSource
import com.a02.draw.feature.home.common.navigation.bindBottomNavigation
import com.a02.draw.feature.home.common.navigation.navigateBottom
import com.a02.draw.feature.home.databinding.ScreenMainHomeBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class HomeFragment : BaseFragment<ScreenMainHomeBinding>(ScreenMainHomeBinding::inflate) {
    private val viewModel: HomeViewModel by viewModels()
    private val imageLoader = HomeImageLoader()
    private var sourceCaptureFile: File? = null
    private val topicAdapter by lazy {
        HomeTopicAdapter(imageLoader) { viewModel.onAction(HomeAction.OpenTopic(it)) }
    }

    private val picker =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let {
                runCatching {
                    requireContext().contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                viewModel.onAction(HomeAction.MediaSelected(it.toString()))
            }
        }

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) openSourceCamera() else showError(R.string.camera_permission_required)
    }

    private val sourceCamera =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            val file = sourceCaptureFile
            sourceCaptureFile = null
            if (saved && file != null) {
                viewModel.onAction(HomeAction.MediaSelected(fileProviderUri(file).toString()))
            } else {
                file?.delete()
            }
        }

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.header.applyStatusBarPadding(lightStatusBarIcons = false)
        sourceCaptureFile = savedInstanceState?.getString(SOURCE_FILE_KEY)?.let(::File)
        binding.topicList.setAdaptiveGridLayoutManager(
            minimumItemWidth = resources.getDimensionPixelSize(R.dimen.home_topic_min_cell_width),
            minimumSpanCount = 2,
        )
        binding.topicList.adapter = topicAdapter
        binding.searchCard.setDebouncedClickListener { viewModel.onAction(HomeAction.OpenSearch) }
        binding.gallerySource.setOnClickListener { viewModel.onAction(HomeAction.OpenSourceModal) }
        binding.aiSource.setDebouncedClickListener { viewModel.onAction(HomeAction.OpenAiGallery) }
        binding.webSource.setDebouncedClickListener { viewModel.onAction(HomeAction.OpenWebSearch) }
        binding.cameraSourceOption.setOnClickListener {
            viewModel.onAction(HomeAction.SelectSource(DeviceImageSource.CAMERA))
        }
        binding.gallerySourceOption.setOnClickListener {
            viewModel.onAction(HomeAction.SelectSource(DeviceImageSource.GALLERY))
        }
        binding.confirmSourceButton.setDebouncedClickListener { viewModel.onAction(HomeAction.ConfirmSource) }
        binding.sourceModal.setOnClickListener { viewModel.onAction(HomeAction.CloseSourceModal) }
        binding.bottomNavigationInclude.bindBottomNavigation(BottomDestination.HOME) {
            viewModel.onAction(HomeAction.OpenBottomDestination(it))
        }
    }

    override fun observeData() {
        collectWhenStarted {
            launch {
                viewModel.state.collect { state ->
                    val showSkeleton = state.isLoading && state.topics.isEmpty()
                    topicAdapter.submitList(state.topics)
                    binding.topicLoadingSkeleton.isVisible = showSkeleton
                    binding.topicList.isVisible = !showSkeleton
                    binding.sourceModal.isVisible = state.isSourceModalVisible
                    binding.cameraSourceOption.isChecked =
                        state.selectedSource == DeviceImageSource.CAMERA
                    binding.gallerySourceOption.isChecked =
                        state.selectedSource == DeviceImageSource.GALLERY
                }
            }
            launch { viewModel.effects.collect(::handleEffect) }
        }
    }

    private fun handleEffect(effect: HomeScreenEffect) {
        when (effect) {
            HomeScreenEffect.NavigateSearch -> findNavController().navigate(R.id.searchFragment)
            is HomeScreenEffect.NavigateGallery -> findNavController().navigate(
                R.id.galleryFragment,
                Bundle().apply { putString(ARG_TOPIC_ID, effect.topicId) },
            )

            HomeScreenEffect.NavigateTutorial -> findNavController().navigate(R.id.tutorialCameraFragment)
            HomeScreenEffect.OpenPhotoPicker -> picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )

            HomeScreenEffect.OpenSourceCamera -> openSourceCamera()
            HomeScreenEffect.OpenWebSearch -> runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, WEB_SEARCH.toUri()))
            }.onFailure { showError(R.string.generic_error) }

            is HomeScreenEffect.NavigateBottom -> findNavController().navigateBottom(effect.destination)
        }
    }

    private fun openSourceCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        val directory = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: return showError(R.string.generic_error)
        if (!directory.exists() && !directory.mkdirs()) {
            showError(R.string.generic_error)
            return
        }
        val file = File(directory, "source-${System.currentTimeMillis()}.jpg")
        sourceCaptureFile = file
        runCatching {
            sourceCamera.launch(fileProviderUri(file))
        }.onFailure {
            file.delete()
            sourceCaptureFile = null
            showError(R.string.generic_error)
        }
    }

    private fun fileProviderUri(file: File) = FileProvider.getUriForFile(
        requireContext(),
        "${requireContext().packageName}.files",
        file,
    )

    private fun showError(message: Int) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        sourceCaptureFile?.absolutePath?.let { outState.putString(SOURCE_FILE_KEY, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding.topicList.adapter = null
        imageLoader.close()
        super.onDestroyView()
    }

    private companion object {
        const val SOURCE_FILE_KEY = "home.source.file"
        const val ARG_TOPIC_ID = "topicId"
        const val WEB_SEARCH = "https://www.google.com/search?tbm=isch&q=drawing+reference"
    }
}
