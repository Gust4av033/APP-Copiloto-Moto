package com.gust4av033.appcopilotomoto

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/** Runs synthetic model inputs on the device. Never opens or grants the microphone. */
class ModelCompatibilityInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            val env = OrtEnvironment.getEnvironment()
            val models = targetContext.assets.list("openwakeword").orEmpty().filter { it.endsWith(".onnx") }
            check(models.size >= 4) { "Missing bundled models" }
            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(1)
                models.forEach { file ->
                    val shape = when (file) {
                        "melspectrogram.onnx" -> longArrayOf(1, 1280)
                        "embedding_model.onnx" -> longArrayOf(1, 76, 32, 1)
                        else -> longArrayOf(1, 16, 96)
                    }
                    val values = FloatArray(shape.fold(1L, Long::times).toInt())
                    val bytes = targetContext.assets.open("openwakeword/$file").use { it.readBytes() }
                    env.createSession(bytes, options).use { session ->
                        OnnxTensor.createTensor(env, FloatBuffer.wrap(values), shape).use { input ->
                            session.run(mapOf(session.inputNames.first() to input)).use { output ->
                                val floats = (output[0] as OnnxTensor).floatBuffer
                                check(floats.hasRemaining()) { "Empty output: $file" }
                                while (floats.hasRemaining()) check(floats.get().isFinite()) { "Invalid output: $file" }
                            }
                        }
                    }
                }
            }
            result.putInt("models_checked", models.size)
            result.putString("stream", "Synthetic ONNX inference passed; microphone never opened.\n${models.joinToString("\n")}")
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            result.putString("stream", "ONNX compatibility FAILED: ${failure.javaClass.simpleName}: ${failure.message}")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
