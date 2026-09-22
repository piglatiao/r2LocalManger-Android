package com.r2manager.android.data.remote.s3

/**
 * S3 错误码常量（逐字取自桌面版 `R2Client._classifyError` 判定用到的 name）。
 *
 * 与 `core.error.ErrorMapper.classify(s3Code, httpStatus)` 配合使用，保证分类优先级一致。
 */
object S3ErrorCodes {
    // 认证
    const val INVALID_ACCESS_KEY_ID = "InvalidAccessKeyId"
    const val SIGNATURE_DOES_NOT_MATCH = "SignatureDoesNotMatch"
    const val INVALID_TOKEN = "InvalidToken"
    const val EXPIRED_TOKEN = "ExpiredToken"
    const val TOKEN_REFRESH_REQUIRED = "TokenRefreshRequired"

    // 权限
    const val ACCESS_DENIED = "AccessDenied"
    const val SIGNATURE_MISMATCH = "SignatureMismatch"

    // 桶
    const val NO_SUCH_BUCKET = "NoSuchBucket"
    const val BUCKET_NOT_FOUND = "BucketNotFound"
    const val BUCKET_ALREADY_EXISTS = "BucketAlreadyExists"
    const val BUCKET_ALREADY_OWNED_BY_YOU = "BucketAlreadyOwnedByYou"
    const val INVALID_BUCKET_NAME = "InvalidBucketName"

    // 对象
    const val NO_SUCH_KEY = "NoSuchKey"
    const val NOT_FOUND = "NotFound"

    // Multipart
    const val NO_SUCH_UPLOAD = "NoSuchUpload"
    const val INVALID_PART = "InvalidPart"
    const val INVALID_PART_ORDER = "InvalidPartOrder"
    const val ENTITY_TOO_SMALL = "EntityTooSmall"
}
