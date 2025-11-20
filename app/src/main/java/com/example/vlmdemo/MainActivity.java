package com.example.vlmdemo;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Simple demo:
 * 1. Capture image using camera (thumbnail).
 * 2. Send image + prompt to Gemini.
 * 3. Show Gemini text response in a scrollable TextView.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GeminiDemo";

    private static final int CAMERA_REQUEST_CODE = 1;

    private ImageView imageView;
    private TextView textView;

    // TODO: Put your own API key here (do NOT commit real keys to GitHub).
    private static final String API_KEY = "YOUR_API_KEY_HERE";
    private static final String GEMINI_MODEL = "gemini-2.5-flash";
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + GEMINI_MODEL + ":generateContent?key=" + API_KEY;

    /**
     * JSON template for Gemini request.
     * %s (1st) -> Base64 image string
     * %s (2nd) -> Text prompt
     */
    private static final String GEMINI_JSON_TEMPLATE =
            "{"
                    + "\"contents\": ["
                    + "  {"
                    + "    \"parts\": ["
                    + "      {"
                    + "        \"inline_data\": {"
                    + "          \"mime_type\": \"image/jpeg\","
                    + "          \"data\": \"%s\""
                    + "        }"
                    + "      },"
                    + "      {"
                    + "        \"text\": \"%s\""
                    + "      }"
                    + "    ]"
                    + "  }"
                    + "]"
                    + "}";

    private OkHttpClient httpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI references
        imageView = findViewById(R.id.imageView);
        textView = findViewById(R.id.textView);

        // Make TextView scrollable
        textView.setMovementMethod(new ScrollingMovementMethod());

        // Build OkHttp client once
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Called by a button to open the camera app.
     * NOTE: This returns only a small thumbnail image.
     * For full-resolution images, use EXTRA_OUTPUT with FileProvider.
     */
    public void startCamera(View view) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, CAMERA_REQUEST_CODE);
    }

    /**
     * Receive result from camera intent.
     * Here we get the thumbnail bitmap from "data" extras.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Bundle extras = data.getExtras();
            if (extras != null) {
                Bitmap image = (Bitmap) extras.get("data");
                if (image != null) {
                    imageView.setBackgroundColor(Color.TRANSPARENT);
                    imageView.setImageBitmap(image);
                    return;
                }
            }
            Log.e(TAG, "No bitmap found in camera result.");
        } else if (requestCode == CAMERA_REQUEST_CODE) {
            Log.e(TAG, "Camera cancelled or failed.");
        }
    }

    /**
     * Called by a button to start Gemini request
     * using the current image in the ImageView.
     */
    public void startGemini(View view) {
        if (imageView.getDrawable() == null) {
            textView.setText("Please capture an image first.");
            Log.e(TAG, "No image in ImageView.");
            return;
        }

        Bitmap image = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
        textView.setText("Please wait, Gemini is analyzing the image...");

        try {
            callGemini(image);
        } catch (Exception e) {
            Log.e(TAG, "startGemini error: " + e);
            textView.setText("Error: " + e.getMessage());
        }
    }

    /**
     * Send image + prompt to Gemini API using OkHttp.
     */
    private void callGemini(Bitmap bitmap) throws Exception {
        // 1. Resize image and encode as Base64
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, 512, 512, true);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        resized.compress(Bitmap.CompressFormat.JPEG, 90, stream);
        byte[] imageBytes = stream.toByteArray();
        String imageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

        // 2. Build JSON body (image + text prompt)
        String jsonBody = String.format(GEMINI_JSON_TEMPLATE, imageBase64, "Describe this image.");
        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));

        // 3. Create POST request
        Request request = new Request.Builder()
                .url(GEMINI_URL)
                .post(body)
                .build();

        // 4. Execute request asynchronously
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Gemini onFailure: " + e);
                runOnUiThread(() ->
                        textView.setText("Request failed: " + e.getMessage())
                );
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Gemini error: " + response.code() + " " + response.message());
                    runOnUiThread(() ->
                            textView.setText("Error: " + response.code() + " " + response.message())
                    );
                    return;
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "Gemini raw response: " + responseBody);
                printResponse(responseBody);
            }
        });

        Log.d(TAG, "Gemini request sent");
    }

    /**
     * Parse Gemini JSON and show the text in the TextView.
     * We read: candidates[0].content.parts[*].text
     */
    private void printResponse(String responseJson) {
        try {
            JSONObject root = new JSONObject(responseJson);
            JSONArray candidates = root.getJSONArray("candidates");
            if (candidates.length() == 0) {
                Log.e(TAG, "No candidates in response.");
                runOnUiThread(() -> textView.setText("No candidates in response."));
                return;
            }

            JSONObject firstCandidate = candidates.getJSONObject(0);
            JSONObject content = firstCandidate.getJSONObject("content");
            JSONArray parts = content.getJSONArray("parts");

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.getJSONObject(i);
                if (part.has("text")) {
                    if (sb.length() > 0) sb.append("\n\n");
                    sb.append(part.getString("text"));
                }
            }

            String finalText = sb.toString();
            runOnUiThread(() -> textView.setText(finalText));

        } catch (Exception e) {
            Log.e(TAG, "printResponse error: " + e);
            runOnUiThread(() ->
                    textView.setText("Failed to parse response: " + e.getMessage())
            );
        }
    }
}