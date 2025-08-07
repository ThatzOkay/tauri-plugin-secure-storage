package nl.thatzokay.secureStorage

import app.tauri.plugin.Invoke

public class KeyStoreException(
    kind: ErrorKind,
    osException: Throwable? = null
    ) : Throwable() {

        val code: String
        override val message: String

        init {
            val template = errorMap[kind]
            val osName = osException?.javaClass?.simpleName

            message = when (kind) {
                ErrorKind.osError,
                ErrorKind.unknownError -> template?.let { String.format(it, osName) }
                    ?: "Unknown error"

                else -> template ?: "Unknown error"
            }

            code = kind.toString()
        }

        fun rejectCall(call: Invoke) {
            call.reject(message, code)
        }

        companion object {
            private val errorMap = hashMapOf(
                ErrorKind.missingKey to "Empty key or missing key param",
                ErrorKind.invalidData to "The data in the store is in an invalid format",
                ErrorKind.osError to "An OS error occurred (%s)",
                ErrorKind.unknownError to "An unknown error occurred: %s"
            )

            fun reject(call: Invoke, kind: ErrorKind, osException: Throwable? = null) {
                val ex = KeyStoreException(kind, osException)
                call.reject(ex.message, ex.code)
            }

            fun reject(call: Invoke, kind: ErrorKind) {
                reject(call, kind, null)
            }
        }

        enum class ErrorKind {
            missingKey,
            invalidData,
            osError,
            unknownError
        }
    }