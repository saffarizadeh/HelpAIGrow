package com.helpaigrow;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.polly.AmazonPollyPresigningClient;
import com.amazonaws.services.polly.model.OutputFormat;
import com.amazonaws.services.polly.model.Engine;
import com.amazonaws.services.polly.model.SynthesizeSpeechPresignRequest;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


class ResponseServer {

    private final ExecutorService executorService;
    private String finalUtterance;

    // Amazon Polly
    private static AmazonPollyPresigningClient client;

    private String responseServerAddress;
    private Future<String> speakingAgent;

    // Settings
    private static final String USERSETTINGS = "PrefsFile";
    private final SharedPreferences settings;

    private String conversationToken;
    private static String voicePersona;
    private SpeechActivity activity;
    private ArrayList<String> receivedMessage;

    ResponseServer(SpeechActivity activity) {

        this.activity = activity;

        // Restore preferences
        settings = activity.getSharedPreferences(USERSETTINGS, 0);
        conversationToken = settings.getString("conversationToken", "");
        voicePersona = settings.getString("voicePersona", "Joanna");

        executorService = Executors.newFixedThreadPool(100);

        initPollyClient();
    }


    private void initPollyClient() {
        CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                activity,
                "us-east-1:fc6c9972-5172-4538-a1b2-5c46b097002a", // Identity pool ID
                Regions.US_EAST_1 // Region
        );
        client = new AmazonPollyPresigningClient(credentialsProvider);
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    void respond() {
        FetchResponse fetchResponseTask = new FetchResponse(prepareQuery());
        Future<String> fetchResponseFuture = executorService.submit(fetchResponseTask);
        ProcessResponse processResponseTask = null;
        try {
            processResponseTask = new ProcessResponse(fetchResponseFuture.get());
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        assert processResponseTask != null;
        Future<HashMap> processResponseFuture = executorService.submit(processResponseTask);
        HashMap processedResponse = null;
        try {
            processedResponse = processResponseFuture.get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        assert processedResponse != null;
        // Running UI on a separate thread let's the voice to load much faster

        Speak speakTask = new Speak(processedResponse);
        speakingAgent = executorService.submit(speakTask);

        final HashMap finalProcessedResponse = processedResponse;
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    activity.playPollySound(speakingAgent.get(), finalUtterance, (boolean) finalProcessedResponse.get("isFinished"));
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        this.flushReceivedMessage();
        activity.runOnUiThread(new RunCommandOnUi(processedResponse));
    }


    void stopSpeaking() {
        if (speakingAgent != null) {
            speakingAgent.cancel(true);
            speakingAgent = null;
        }
        activity.stopMediaPlayer();
    }

    private String prepareQuery() {
        String text;
        ArrayList<String> messages = getReceivedMessage();
        try {
            text = TextUtils.join(". ", messages);
        } catch (Exception e) {
            text = "";
        }
        finalUtterance = text;

        @SuppressLint("DefaultLocale") String query = String.format("conversation_token=%s&message=%s", conversationToken, text);
        return query;
    }

    private class RunCommandOnUi implements Runnable {
        final HashMap command;

        RunCommandOnUi(final HashMap command){
            this.command = command;
        }
        @Override
        public void run() {
            if ((int) command.get("responseCode") != 0) {
                activity.runCommand(
                        (int) command.get("responseCode"),
                        (int) command.get("fulfillment"),
                        String.valueOf(command.get("responseParameter")),
                        String.valueOf(command.get("nextCommandHintText")),
                        (boolean) command.get("hasTriedAllCommands"),
                        (boolean) command.get("commandCompleted")
                );
            }
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    /**
     * Getters and Setters
     */
    private ArrayList<String> getReceivedMessage() {
        return receivedMessage;
    }

    void setReceivedMessage(ArrayList<String> receivedMessage) {
        Log.d("Location", "setReceivedMessage");
        this.receivedMessage = receivedMessage;
    }

    void setResponseServerAddress(String responseServerAddress) {
        this.responseServerAddress = responseServerAddress;
    }

    private void flushReceivedMessage() {
        Log.d("Location", "flushReceivedMessage");
        if (this.receivedMessage != null) {
            this.receivedMessage.clear();
        }
    }

    private class Speak implements Callable<String> {
        String textToRead;
        boolean isSSML;
        boolean isNeural;
        String region;
        String textType;
        String engine;

        Speak(HashMap response){
            if (!String.valueOf(response.get("responseText")).equals("")) {
                setParams(
                        String.valueOf(response.get("responseText")),
                        (boolean) response.get("isSSML"),
                        (boolean) response.get("isNeural"),
                        String.valueOf(response.get("region"))
                );
                if ((int) response.get("conversationTurn") != 0) {
                    activity.setConversationTurn((int) response.get("conversationTurn"));
                }
            }
        }

        private void setParams(String textToRead, boolean isSSML, boolean isNeural, String region) {
            this.isSSML = isSSML;
            this.isNeural = isNeural;
            this.region = region;
            if (isNeural) {
                engine = "neural";
                textToRead = "<amazon:domain name=\"conversational\">" + textToRead + "</amazon:domain>";
            } else {
                engine = "standard";
            }
            if (isSSML) {
                textType = "ssml";
                textToRead = "<speak>" + textToRead + "</speak>";
            } else {
                textType = "text";
            }
            this.textToRead = textToRead;
        }

        @Override
        public String call() {
            try {
                // Create speech synthesis request.
                SynthesizeSpeechPresignRequest synthesizeSpeechPresignRequest =
                        new SynthesizeSpeechPresignRequest()
                                // Set text to synthesize.
                                .withText(this.textToRead)
                                // Set voice selected by the user.
                                .withVoiceId(voicePersona)
                                // Set format to MP3.
                                .withOutputFormat(OutputFormat.Mp3)
                                .withSampleRate("22050")
                                .withTextType(this.textType)
                                .withEngine(Engine.fromValue(this.engine));
                // Get the presigned URL for synthesized speech audio stream.
                URL presignedSynthesizeSpeechUrl =
                        client.getPresignedSynthesizeSpeechUrl(synthesizeSpeechPresignRequest);
                Log.i("Polly", "Received speech presigned URL: " + presignedSynthesizeSpeechUrl);
                return presignedSynthesizeSpeechUrl.toString();
            }
            catch (Exception e) {
                Log.d("Polly Service", "Failed");
                return null;
            }
        }
    }

    private class FetchResponse implements Callable<String> {
        String urlParameters;

        FetchResponse(String urlParameters) {
            this.urlParameters = urlParameters;
        }

        @Override
        public String call() {
            StringBuilder result = new StringBuilder();
            try {
                Log.d("urlParameters", urlParameters);
                URL url = new URL(responseServerAddress);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(10000);
                conn.setConnectTimeout(15000);
                conn.setRequestMethod("POST");
                conn.setDoInput(true);
                conn.setDoOutput(true);
                OutputStream os = conn.getOutputStream();
                DataOutputStream writer = new DataOutputStream(os);
                writer.writeBytes(urlParameters);
                writer.flush();
                writer.close();
                os.close();
                int status = conn.getResponseCode();
                Log.d("Location", "HTTP STATUS: " + String.valueOf(status));
                InputStream inputStream = conn.getInputStream();
                BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                for (int c; (c = in.read()) >= 0; )
                    result.append((char) c);
            } catch (IOException e) {
                Log.d("Location", "IOException");
                e.printStackTrace();
            }
            return result.toString();
        }
    }

    private class ProcessResponse implements Callable<HashMap> {
        String result;

        ProcessResponse(String result){
            this.result = result;
        }

        @Override
        public HashMap call() {
            HashMap<String, java.io.Serializable> responseMap = new HashMap<String, java.io.Serializable>();
            try {
                JSONObject resultJSON = new JSONObject(result);
                JSONObject response = resultJSON.getJSONObject("response");

                responseMap.put("responseText", response.getString("message"));
                responseMap.put("isFinished", response.getBoolean("is_finished"));
                responseMap.put("isSSML", response.getBoolean("is_ssml"));
                responseMap.put("isNeural", response.getBoolean("is_neural"));
                responseMap.put("region", response.getString("region"));
                responseMap.put("responseCode", response.getInt("response_code"));
                responseMap.put("fulfillment", response.getInt("fulfillment"));

                try {
                    responseMap.put("conversationTurn", response.getInt("conversation_turn"));
                } catch (Exception ignored) {
                    responseMap.put("conversationTurn", 1);
                }
                if (response.getInt("response_code") != 0) {
                    responseMap.put("commandCompleted", response.getBoolean("command_completed"));
                    responseMap.put("responseParameter", response.getString("response_parameter"));
                    responseMap.put("nextCommandHintText", response.getString("next_command_hint_text"));
                    responseMap.put("hasTriedAllCommands", response.getBoolean("has_tried_all_commands"));
                }
            } catch (JSONException e) {
                e.printStackTrace();
                Log.d("ServerStat", e.toString());
            }
            return responseMap;
        }
    }
}
