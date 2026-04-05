package com.ormec.myapplication;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

import com.ormec.myapplication.models.MeterReading;

/**
 * Helper class to load and run the TensorFlow Lite consumption prediction model.
 *
 * Model expects input shape: [1, 60, 1] - batch of 1, sequence of 60 readings, 1 feature per reading
 * Model outputs shape: [1, 1] - a single predicted kWh value (scaled)
 *
 * IMPORTANT: Update TRAIN_MIN_KWH and TRAIN_MAX_KWH to match your model's training scaler!
 */
public class ConsumptionPredictor {

    private static final String TAG = "ConsumptionPredictor";
    private static final String MODEL_FILENAME = "consumption_model.tflite";

    // Sequence length expected by the model (must match training)
    private static final int SEQ_LENGTH = 60;

    // MinMaxScaler parameters from training (UPDATE THESE!)
    // These should match the values in your Python scaler_info.json
    private static final float TRAIN_MIN_KWH = 5.0f;   // Replace with actual min from training
    private static final float TRAIN_MAX_KWH = 70.0f;  // Replace with actual max from training

    private Interpreter interpreter;
    private boolean isModelLoaded = false;

    public ConsumptionPredictor(Context context) {
        try {
            MappedByteBuffer modelBuffer = loadModelFile(context);
            interpreter = new Interpreter(modelBuffer);
            isModelLoaded = true;
            Log.i(TAG, "TFLite model loaded successfully");
        } catch (IOException e) {
            Log.e(TAG, "Error loading TFLite model", e);
            isModelLoaded = false;
        }
    }

    /**
     * Load the TFLite model from assets folder
     */
    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILENAME);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /**
     * Predict next month's consumption based on historical readings
     *
     * @param readings List of historical meter readings (should be sorted by date ascending)
     * @return Predicted kWh consumption, or -1 if prediction failed
     */
    public float predict(List<MeterReading> readings) {
        if (!isModelLoaded || interpreter == null) {
            Log.w(TAG, "Model not loaded, cannot predict");
            return -1f;
        }

        if (readings == null || readings.isEmpty()) {
            Log.w(TAG, "No readings provided");
            return -1f;
        }

        if (readings.size() < SEQ_LENGTH) {
            Log.w(TAG, "Not enough readings: need " + SEQ_LENGTH + ", got " + readings.size());
            return -1f;
        }

        try {
            // Prepare input: [1, 60, 1] shape
            float[][][] input = new float[1][SEQ_LENGTH][1];

            // Take the last 60 readings (most recent)
            int startIdx = readings.size() - SEQ_LENGTH;
            for (int i = 0; i < SEQ_LENGTH; i++) {
                float rawKwh = readings.get(startIdx + i).getKwh();

                // Apply MinMaxScaler: scaled = (x - min) / (max - min)
                float scaled = (rawKwh - TRAIN_MIN_KWH) / (TRAIN_MAX_KWH - TRAIN_MIN_KWH);
                input[0][i][0] = scaled;
            }

            // Prepare output: [1, 1] shape
            float[][] output = new float[1][1];

            // Run inference
            interpreter.run(input, output);

            float predictedScaled = output[0][0];

            // Inverse transform: x = scaled * (max - min) + min
            float predictedKwh = predictedScaled * (TRAIN_MAX_KWH - TRAIN_MIN_KWH) + TRAIN_MIN_KWH;

            Log.d(TAG, "Prediction successful: " + predictedKwh + " kWh");
            return predictedKwh;

        } catch (Exception e) {
            Log.e(TAG, "Error running prediction", e);
            return -1f;
        }
    }

    /**
     * Alternative prediction using simple averaging as fallback
     */
    public float predictWithAverage(List<MeterReading> readings) {
        if (readings == null || readings.isEmpty()) {
            return 0f;
        }

        float sum = 0f;
        for (MeterReading r : readings) {
            sum += r.getKwh();
        }
        return sum / readings.size();
    }

    /**
     * Check if the model is loaded and ready
     */
    public boolean isReady() {
        return isModelLoaded && interpreter != null;
    }

    /**
     * Get the required sequence length
     */
    public int getRequiredSequenceLength() {
        return SEQ_LENGTH;
    }

    /**
     * Clean up resources
     */
    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
            Log.i(TAG, "TFLite model closed");
        }
        isModelLoaded = false;
    }
}