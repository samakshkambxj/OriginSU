package com.originsu.manager.domain.usecase

import com.originsu.manager.domain.text.TextTransliterator

class TransliterateTextUseCase(private val transliterator: TextTransliterator) {
    operator fun invoke(value: String): String = transliterator.transliterate(value)
}
