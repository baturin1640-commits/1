package com.autovideo.app

fun RemovableSource?.safeDisplayName(): String = when {
    this == null -> "Накопитель не выбран"
    uriString == INTERNAL_SOURCE_URI -> "Внутренняя память"
    else -> "Внешний накопитель"
}
