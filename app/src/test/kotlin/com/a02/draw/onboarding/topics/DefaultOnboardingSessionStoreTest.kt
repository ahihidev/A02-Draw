package com.a02.draw.onboarding.topics

import com.a02.draw.domain.model.ContentImage
import com.a02.draw.domain.model.DrawingTopic
import com.a02.draw.onboarding.common.session.DefaultOnboardingSessionStore
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultOnboardingSessionStoreTest {
    @Test
    fun `onboarding keeps the complete topic list used by home`() {
        val topics = (1..12).map { index ->
            DrawingTopic(
                id = "topic-$index",
                title = "Topic $index",
                image = ContentImage(localKey = "topic_$index"),
            )
        }
        val store = DefaultOnboardingSessionStore()

        store.setTopics(topics)

        assertEquals(topics, store.state.value.topics)
    }
}
