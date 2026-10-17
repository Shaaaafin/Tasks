package dev.quiettasks.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TagsUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun timeout() {
        Class.forName("androidx.test.espresso.IdlingPolicies").getMethod("setMasterPolicyTimeout", java.lang.Long.TYPE, java.util.concurrent.TimeUnit::class.java)
            .invoke(null, 10L, java.util.concurrent.TimeUnit.SECONDS)
    }
    @Test fun editTagsAndRemoveExistingTag() {
        var tags by mutableStateOf(listOf("existing"))
        rule.setContent { TasksTheme { TagsButton(tags) { tags = it } } }
        rule.onNodeWithText("Tags (1)").performClick()
        rule.onNodeWithContentDescription("Remove tag existing").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("work, errands")
        rule.onNodeWithText("Done").performClick()
        rule.runOnIdle { assertEquals(listOf("work", "errands"), tags) }
    }
}
