package com.gust4av033.appcopilotomoto;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Collections;

/** Synthetic inference only. Java avoids Kotlin helpers removed from the R8 target APK. */
public final class ModelCompatibilityInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            String[] files = getTargetContext().getAssets().list("openwakeword");
            int count = 0;
            StringBuilder checked = new StringBuilder();
            try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
                options.setIntraOpNumThreads(1);
                if (files == null) throw new IllegalStateException("Missing model directory");
                for (String file : files) {
                    if (!file.endsWith(".onnx")) continue;
                    long[] shape;
                    if (file.equals("melspectrogram.onnx")) shape = new long[]{1, 1280};
                    else if (file.equals("embedding_model.onnx")) shape = new long[]{1, 76, 32, 1};
                    else shape = new long[]{1, 16, 96};
                    int size = 1;
                    for (long dimension : shape) size *= (int) dimension;
                    byte[] bytes;
                    try (InputStream input = getTargetContext().getAssets().open("openwakeword/" + file);
                         ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                        byte[] chunk = new byte[8192];
                        int read;
                        while ((read = input.read(chunk)) != -1) buffer.write(chunk, 0, read);
                        bytes = buffer.toByteArray();
                    }
                    try (OrtSession session = env.createSession(bytes, options);
                         OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(new float[size]), shape);
                         OrtSession.Result output = session.run(Collections.singletonMap(session.getInputNames().iterator().next(), input))) {
                        FloatBuffer values = ((OnnxTensor) output.get(0)).getFloatBuffer();
                        if (!values.hasRemaining()) throw new IllegalStateException("Empty output: " + file);
                        while (values.hasRemaining()) {
                            float value = values.get();
                            if (Float.isNaN(value) || Float.isInfinite(value))
                                throw new IllegalStateException("Invalid output: " + file);
                        }
                    }
                    count++;
                    checked.append(file).append('\n');
                }
            }
            if (count < 4) throw new IllegalStateException("Missing bundled models");
            result.putInt("models_checked", count);
            result.putString("stream", "Synthetic ONNX inference passed; microphone never opened.\n" + checked);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "ONNX compatibility FAILED: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
