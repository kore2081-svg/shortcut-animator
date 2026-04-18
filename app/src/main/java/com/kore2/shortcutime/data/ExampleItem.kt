package com.kore2.shortcutime.data

import java.util.UUID

data class ExampleItem(
    val id: String = UUID.randomUUID().toString(),
    val english: String = "",
    val korean: String = "",
    val sourceType: ExampleSourceType = ExampleSourceType.MANUAL,
)
