package com.kore2.shortcutime.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun `모든 5개 공급자가 카탈로그에 있다`() {
        ProviderId.values().forEach { id ->
            assertNotNull("$id 누락", ModelCatalog.modelsFor(id))
        }
        assertEquals(5, ProviderId.values().size)
    }

    @Test
    fun `각 공급자마다 최소 1개 이상의 추천 모델이 있다`() {
        ProviderId.values().forEach { id ->
            val models = ModelCatalog.modelsFor(id)
            assertTrue("$id 에 추천 모델 없음", models.any { it.isRecommended })
        }
    }

    @Test
    fun `recommendedModelId 가 modelsFor 목록 안에 포함된다`() {
        ProviderId.values().forEach { id ->
            val recommended = ModelCatalog.recommendedModelId(id)
            val ids = ModelCatalog.modelsFor(id).map { it.id }
            assertTrue("$id recommended=$recommended 가 목록에 없음", recommended in ids)
        }
    }
}
