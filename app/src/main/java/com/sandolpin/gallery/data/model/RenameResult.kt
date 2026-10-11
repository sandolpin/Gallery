package com.sandolpin.gallery.data.model

/** 名前の変更の結果。 */
enum class RenameResult {
    /** 変更できた。 */
    Success,

    /** 同じフォルダに、同じ名前のファイルがすでにある。 */
    AlreadyExists,

    /** 権限がない、ファイルがない、などで変更できなかった。 */
    Failed,
}