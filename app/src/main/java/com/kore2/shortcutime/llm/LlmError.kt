package com.kore2.shortcutime.llm

sealed class LlmError {
    object Network : LlmError()
    object Timeout : LlmError()
    object InvalidKey : LlmError()
    object RateLimited : LlmError()
    object ServerError : LlmError()
    object Truncated : LlmError()
    object ParseFailure : LlmError()
    object ContentFiltered : LlmError()
    data class Unknown(val message: String) : LlmError()
}

class LlmException(val error: LlmError) : Exception(error.toString())
