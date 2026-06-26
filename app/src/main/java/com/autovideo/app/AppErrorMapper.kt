package com.autovideo.app

import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object AppErrorMapper {
    fun userMessage(error: Throwable?): String = when (error) {
        null -> "Неизвестная ошибка"
        is SecurityException -> "Нет доступа. Проверьте разрешения или выберите накопитель заново."
        is FileNotFoundException -> "Файл недоступен или накопитель отключён."
        is UnknownHostException -> "Нет подключения к интернету или адрес недоступен."
        is SocketTimeoutException -> "Сервер отвечает слишком долго. Повторите попытку."
        is SSLException -> "Не удалось установить защищённое соединение."
        is OutOfMemoryError -> "Недостаточно памяти для открытия материала."
        is IOException -> "Ошибка чтения данных. Проверьте накопитель или подключение."
        else -> "Операцию выполнить не удалось"
    }
}
