package com.er1cmo.noteassistant.notes.ui.components

import androidx.compose.ui.graphics.Color

data class NoteColorOption(
    val name: String,
    val hex: String,
    val color: Color,
)

object NoteColorPalette {
    val options = listOf(
        NoteColorOption("奶油白", "#FFFDF7", Color(0xFFFFFDF7)),
        NoteColorOption("米杏", "#FFF0D9", Color(0xFFFFF0D9)),
        NoteColorOption("浅柠黄", "#FFF2B8", Color(0xFFFFF2B8)),
        NoteColorOption("淡桃粉", "#FFE2D6", Color(0xFFFFE2D6)),
        NoteColorOption("柔雾粉", "#F9E4EF", Color(0xFFF9E4EF)),
        NoteColorOption("薄荷绿", "#E4F6EC", Color(0xFFE4F6EC)),
        NoteColorOption("浅天蓝", "#E7F0FF", Color(0xFFE7F0FF)),
        NoteColorOption("蓝灰", "#E7EBF3", Color(0xFFE7EBF3)),
        NoteColorOption("薰衣草", "#EFE6FF", Color(0xFFEFE6FF)),
        NoteColorOption("卡其灰", "#EFE8D8", Color(0xFFEFE8D8)),
    )

    val default = options.first()

    fun colorFor(hex: String?): Color = options.firstOrNull { it.hex == hex }?.color ?: default.color
}
