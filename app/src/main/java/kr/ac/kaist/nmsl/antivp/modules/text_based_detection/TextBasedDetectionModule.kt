package kr.ac.kaist.nmsl.antivp.modules.text_based_detection

import android.content.Context
import android.os.Bundle
import android.util.Log
import kr.ac.kaist.nmsl.antivp.core.EventType
import kr.ac.kaist.nmsl.antivp.core.Module
import kr.ac.kaist.nmsl.antivp.modules.model_optimization.MobileInferenceManager

class TextBasedDetectionModule(context: Context) : Module() {
    private val TAG = "TextBasedDetection"
    private val mobileInferenceManager = MobileInferenceManager()
    private val vocabList: List<String>


    init {
        subscribeEvent(EventType.TEXT_TRANSCRIBED)
        subscribeEvent(EventType.SMS_RCVD)
        val vocabFile = context.assets.open("vocab.txt")
        val vocabText = vocabFile.bufferedReader().use { it.readText() }
        vocabList = vocabText.split("\n").map { it.trim() }
    }

    override fun name(): String {
        return "text_based_detection"
    }

    override fun handleEvent(type: EventType, bundle: Bundle) {
        when(type) {
            EventType.TEXT_TRANSCRIBED -> {
                Log.d(TAG, "Rcvd a transcribed call dialogue.")

                val dialogue = bundle.getStringArray("dialogue")!!
                for (utter in dialogue)
                    Log.d(TAG, utter)

                val phishingType = detectPhishingType(dialogue)

                if (phishingType != null) {
                    bundle.putString("phishing_type", phishingType)
                    Log.d(TAG, "Phishing call detected")
                    raiseEvent(EventType.PHISHING_CALL_DETECTED, bundle)
                }
            }
            EventType.SMS_RCVD -> {
                Log.d(TAG, "Rcvd a SMS.")

                val message = bundle.getStringArray("message_body")!!
                for (word in message)
                    Log.d(TAG, word)

                val phishingType = detectPhishingType(message)

                if (phishingType != null) {
                    bundle.putString("phishing_type", phishingType)
                    Log.d(TAG, "Smishing SMS detected")
                    raiseEvent(EventType.SMISHING_SMS_DETECTED, bundle)
                }
            }
            else -> {
                Log.e(TAG, "Unexpected event type: $type")
            }
        }
    }

    private fun encodeSentence(sentence: String): Triple<List<String>, List<Int>, List<Int>> {
        val words = sentence.split(" ")

        val tokens = mutableListOf<String>()
        val inputIds = mutableListOf<Int>()
        val attentionMask = mutableListOf<Int>()

        for (word in words) {
            var remainingWord = "▁$word"
            while (remainingWord.isNotEmpty()) {
                var i = remainingWord.length
                while (i > 0 && !vocabList.contains(remainingWord.substring(0, i))) {
                    i -= 1
                }
                if (i == 0) {
                    tokens.add("[UNK]")
                    inputIds.add(vocabList.indexOf("[UNK]"))
                    break
                }

                val token = remainingWord.substring(0, i)
                val inputId = vocabList.indexOf(token)
                tokens.add(token)
                inputIds.add(inputId)
                attentionMask.add(1)
                remainingWord = remainingWord.substring(i)
            }
        }
        // Prepend [CLS]
        tokens.add(0, "[CLS]")
        inputIds.add(0, 2)
        attentionMask.add(0, 1)
        // Append [SEP]
        tokens.add("[SEP]")
        inputIds.add(3)
        attentionMask.add(1)
        // Pad tokens and inputIds to maxSeqLength
        val numPadding = 512 - tokens.size
        for (i in 0 until numPadding) {
            tokens.add("[PAD]")
            inputIds.add(1)
            attentionMask.add(0)
        }

        Log.d(TAG, "Input IDs padded: $inputIds")
        Log.d(TAG, "tokens: $tokens")

        return Triple(tokens, inputIds, attentionMask)
    }


    private fun getPhishingType(classValue: Int): String? {
        return when (classValue) {
            0 -> null
            1 -> "phishing"
            else -> ""
        }
    }


    private fun detectPhishingType(dialogue: Array<String>): String? {
        val concatenatedSentence = dialogue.joinToString(separator = " ")
        val (tokens, inputIds, attentionMask) = encodeSentence(concatenatedSentence)

        val inputData = Bundle()
        inputData.putIntArray("input_ids", inputIds.toIntArray())
        inputData.putIntArray("attention_mask", attentionMask.toIntArray())

        val modelPath = "model_bert_finetuned.pt"
        val resultBundle = mobileInferenceManager.performPytorchInference(0, -1, modelPath, inputData)
        Log.d(TAG, "resultBundle: $resultBundle")

        val classValue = resultBundle.getInt("classValue")
        val confidence = resultBundle.getFloat("confidence")
        val phishingType = getPhishingType(classValue)
        Log.d(TAG, "phishingType: $phishingType")

        return phishingType
    }
}