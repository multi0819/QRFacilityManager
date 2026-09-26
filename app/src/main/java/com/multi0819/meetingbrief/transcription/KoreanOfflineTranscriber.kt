package com.multi0819.meetingbrief.transcription

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class KoreanOfflineTranscriber(context: Context) : TranscriptionEngine {
    private val assets = context.applicationContext.assets

    override suspend fun transcribe(
        wav: File,
        checkpointFrame: Long,
        onProgress: (TranscriptionProgress) -> Unit,
    ): String = withContext(Dispatchers.Default) {
        require(wav.exists() && wav.length() > 44) { "변환할 녹음파일이 없습니다." }
        validateModelAssets()
        val reader = WavWindowReader(wav)
        var text = ""
        val recognizer = OfflineRecognizer(assetManager = assets, config = recognizerConfig())
        try {
            reader.windows(checkpointFrame).forEach { window ->
                val stream = recognizer.createStream()
                try {
                    stream.acceptWaveform(window.samples, sampleRate = SAMPLE_RATE)
                    recognizer.decode(stream)
                    text = mergeTranscript(text, recognizer.getResult(stream).text)
                } finally {
                    stream.release()
                }
                val completed = minOf(reader.totalFrames, window.firstFrame + window.samples.size)
                onProgress(TranscriptionProgress(completed, reader.totalFrames, text))
            }
        } finally {
            recognizer.release()
        }
        text.trim()
    }

    private fun validateModelAssets() {
        val available = assets.list(MODEL_DIR)?.toSet().orEmpty()
        val missing = REQUIRED_FILES.filterNot(available::contains)
        check(missing.isEmpty()) { "오프라인 음성 모델이 손상되었습니다: ${missing.joinToString()}" }
    }

    private fun recognizerConfig(): OfflineRecognizerConfig {
        val transducer = OfflineTransducerModelConfig(
            encoder = "$MODEL_DIR/encoder-epoch-99-avg-1.int8.onnx",
            decoder = "$MODEL_DIR/decoder-epoch-99-avg-1.onnx",
            joiner = "$MODEL_DIR/joiner-epoch-99-avg-1.int8.onnx",
        )
        val model = OfflineModelConfig(
            transducer = transducer,
            tokens = "$MODEL_DIR/tokens.txt",
            numThreads = 2,
            debug = false,
            provider = "cpu",
        )
        return OfflineRecognizerConfig(modelConfig = model, decodingMethod = "greedy_search")
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val MODEL_DIR = "sherpa-onnx-zipformer-korean-2024-06-24"
        private val REQUIRED_FILES = listOf(
            "encoder-epoch-99-avg-1.int8.onnx",
            "decoder-epoch-99-avg-1.onnx",
            "joiner-epoch-99-avg-1.int8.onnx",
            "tokens.txt",
        )
    }
}
