package kr.ac.kaist.nmsl.antivp.modules.text_based_detection

import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import kr.ac.kaist.nmsl.antivp.core.EventType
import kr.ac.kaist.nmsl.antivp.core.Module
import kr.ac.kaist.nmsl.antivp.core.ModuleManager
import org.junit.runner.RunWith
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
internal class TextBasedDetectionModuleTest {
    @Test
    fun testModule() {
        val latch = CountDownLatch(1)

        MainScope().launch {
            val mModuleManager = ModuleManager(ApplicationProvider.getApplicationContext())

//            val textBasedDetectionModule = TextBasedDetectionModule()

            val textBasedDetectionModule = TextBasedDetectionModule(ApplicationProvider.getApplicationContext())

            val dummySpeechToTextModule = object: Module() {
                override fun name(): String { return "speech_to_text" }
                override fun handleEvent(type: EventType, bundle: Bundle) {
                    raiseEvent(EventType.TEXT_TRANSCRIBED, bundle)
                }
            }
            val dummySMSReceiverModule = object: Module() {
                override fun name(): String { return "receive_SMS" }
                override fun handleEvent(type: EventType, bundle: Bundle) {
                    raiseEvent(EventType.SMS_RCVD, bundle)
                }
            }
            val dummyPhishingEventReceiverModule = object: Module() {
                init { subscribeEvent(EventType.PHISHING_CALL_DETECTED) }
                override fun name(): String { return "phishing_event_receiver" }
                override fun handleEvent(type: EventType, bundle: Bundle) {
                    when (type) {
                        EventType.PHISHING_CALL_DETECTED -> {
                            latch.countDown()
                        }
                        else -> {
                            throw Exception()
                        }
                    }
                }
            }
            val dummySmishingEventReceiverModule = object: Module() {
                init { subscribeEvent(EventType.SMISHING_SMS_DETECTED) }
                override fun name(): String { return "smishing_event_receiver" }
                override fun handleEvent(type: EventType, bundle: Bundle) {
                    when (type) {
                        EventType.SMISHING_SMS_DETECTED -> {
                            latch.countDown()
                        }
                        else -> {
                            throw Exception()
                        }
                    }
                }
            }

            mModuleManager.register(dummySpeechToTextModule)
            mModuleManager.register(textBasedDetectionModule)
            mModuleManager.register(dummyPhishingEventReceiverModule)
            mModuleManager.register(dummySMSReceiverModule)
            mModuleManager.register(dummySmishingEventReceiverModule)


            val bundle = Bundle()
            bundle.putInt("dialogue_id", 0)
            bundle.putStringArray("dialogue", arrayOf(
                "여보세요",
                "검찰청에서 연락드렸습니다."
            ))
            bundle.putStringArray("message_body", arrayOf(
                "왜? 아직 돈이 안 떨어졌구나 키키"
            ))
            dummySpeechToTextModule.raiseEvent(EventType.TEXT_TRANSCRIBED, bundle)
            dummySMSReceiverModule.raiseEvent(EventType.SMS_RCVD, bundle)
        }
        latch.await(10000, TimeUnit.MILLISECONDS)
    }
}