package com.r2manager.android.domain.model

/**
 * 上传来源入口。
 */
enum class UploadSource {
    /** 照片和视频（Photo Picker，无需权限）。 */
    PHOTO_PICKER,

    /** 文档（SAF ACTION_OPEN_DOCUMENT）。 */
    DOCUMENT_SAF,

    /** 拍照（ACTION_IMAGE_CAPTURE + FileProvider）。 */
    CAMERA
}
