package com.autovideo.app

object AppErrorMapper {
    fun userMessage(error: Throwable?): String = if (error == null) {
        "Неизвестная ошибка"
    } else {
        "Операцию выполнить не удалось"
    }
}
