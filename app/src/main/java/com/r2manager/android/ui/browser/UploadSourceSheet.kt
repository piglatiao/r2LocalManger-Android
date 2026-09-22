package com.r2manager.android.ui.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.r2manager.android.R
import com.r2manager.android.databinding.SheetUploadSourceBinding
import com.r2manager.android.domain.model.UploadSource

/**
 * 上传来源面板：照片和视频（Photo Picker）/ 文档（SAF）/ 拍照（FileProvider）。
 *
 * 一期不申请任何存储权限（SAF / Photo Picker 均无需权限）。
 * 由 [BrowserFragment] 弹出；结果通过 [Listener] 回传。
 */
class UploadSourceSheet : BottomSheetDialogFragment() {

    /** 结果回调。 */
    interface Listener {
        /** 选择了上传来源。 */
        fun onUploadSourceSelected(source: UploadSource)
    }

    private var _binding: SheetUploadSourceBinding? = null
    private val binding get() = checkNotNull(_binding)

    private val listener: Listener? get() = parentFragment as? Listener

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetUploadSourceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.uploadSourcePhotos.setOnClickListener { select(UploadSource.PHOTO_PICKER) }
        binding.uploadSourceDocuments.setOnClickListener { select(UploadSource.DOCUMENT_SAF) }
        binding.uploadSourceCamera.setOnClickListener { select(UploadSource.CAMERA) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun getTheme(): Int = R.style.Theme_R2Manager_BottomSheet

    private fun select(source: UploadSource) {
        listener?.onUploadSourceSelected(source)
        dismiss()
    }
}
