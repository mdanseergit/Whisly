package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ai.translation.IndicTrans2Engine
import com.example.ai.translation.RealOnDeviceTranslationService
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * INSTRUMENTED AUDIT TEST (S0).
 *
 * Runs the REAL translation engine (ML Kit via IndicTrans2Engine) on the reference sentences
 * and prints actual outputs. Requires a device/emulator with Play Services and downloaded models.
 *
 * Status: UNMEASURED until run on a real device. See DIAGNOSIS8.md.
 */
@RunWith(AndroidJUnit4::class)
class RealEngineAuditTest {
    private val sentences = listOf(
        "Could you please tell me how to get to the railway station?",
        "I have been waiting here for more than an hour.",
        "If it rains tomorrow, we will not go to the beach.",
        "She told me that she would call me after dinner.",
        "I did not understand what the teacher explained yesterday.",
        "How much does this shirt cost, and do you have a bigger size?",
        "My brother, who lives in Chennai, is coming home next week.",
        "I'm not feeling well, so I need to see a doctor.",
        "Please don't touch that, it is very hot.",
        "We had already eaten when they arrived.",
        "Where can I find a pharmacy that is open at night?",
        "He said he had never seen such a beautiful place.",
        "The train was late because of the heavy rain.",
        "I want to learn Tamil, but I don't have enough time.",
        "Can you speak slowly? I am still learning.",
        "Thank you very much for your help yesterday.",
        "What time does the last bus leave for Madurai?",
        "They are building a new hospital near our village.",
        "Although he was tired, he finished all his work.",
        "I forgot my wallet at home, so can I pay by phone?",
        "My grandfather used to tell us stories every night.",
        "Is this seat taken?",
        "You should drink more water and sleep early.",
        "The meeting has been moved from Monday to Wednesday at 3:30 PM.",
        "my mom said I was very cute in my childhood but in my adulthood looks like begger",
        "i need to know where is the theatre",
        "I need water"
    )

    @Test
    fun auditRealEngineOutputs() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = IndicTrans2Engine(
            isPackInstalledAndVerified = { true },
            neuralSessionDelegate = { sentence, src, tgt ->
                RealOnDeviceTranslationService.translate(sentence, src, tgt)
            }
        )
        for (s in sentences) {
            val r = engine.translate(s, "en", "ta")
            println("AUDIT_SRC=$s")
            println("AUDIT_OUT=${r.translatedText}")
            println("AUDIT_OK=${r.success}")
        }
    }
}