package com.helpaigrow;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
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
import java.util.ArrayList;

public class ExpTwoGroupOneActivity extends SpeechActivity {

    protected String responseServerUrl = "http://amandabot.xyz/assistant_response/";

    private Toast toastMessage;
    private Switch lightsSwitch;
    private Switch acSwitch;
    private SeekBar acTemperature;
    private TextView time;
    private TextView hintText;

    // Containers
    private ArrayList<String> recognizedTextBuffer;

    // View references
    private ImageButton microphoneIcon;
    protected TextView recognizedText;
    private ProgressBar spinner;
    private TextView loadingWhiteTransparent;
    private Button nextButton;

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Creating the activity page
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exp_two_group_one);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final SharedPreferences settings = getSharedPreferences(USERSETTINGS, 0);
        final String conversationToken = settings.getString("conversationToken", "");

        microphoneIcon = findViewById(R.id.microphoneButtonE2G1);

        spinner = findViewById(R.id.progressBarE2G1);
        spinner.setVisibility(View.GONE);
        loadingWhiteTransparent = findViewById(R.id.loadingWhiteTransparentE2G1);
        loadingWhiteTransparent.setVisibility(View.GONE);

        recognizedText = findViewById(R.id.recognizedTextE2G1);

        recognizedTextBuffer = new ArrayList<>();

        lightsSwitch = findViewById(R.id.lightsSwitchE2G1);
        lightsSwitch.setClickable(false);

        acSwitch = findViewById(R.id.acSwitchE2G1);
        acSwitch.setClickable(false);

        acTemperature = findViewById(R.id.acTemperatureE2G1);
        acTemperature.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return true;
            }
        });

        time = findViewById(R.id.timeE2G1);

        hintText = findViewById(R.id.hintE2G1);

        nextButton = findViewById(R.id.nextButtonE2G1);
        nextButton.setVisibility(View.INVISIBLE);
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                @SuppressLint("DefaultLocale") String query = String.format("conversation_token=%s&message=", conversationToken);
//                Log.d("conversationToken", query);
                new FetchResponse().execute(query);
            }
        });

        responseServer = new ResponseServer(this);
        responseServer.setResponseServerAddress(getResponseServerUrl());

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        assert audioManager != null;
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * 3) / 4, 0);
        audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }


    @Override
    public void onBackPressed() {
        onStop();
        Intent goBackIntent = new Intent(ExpTwoGroupOneActivity.this, WelcomeActivity.class);
        goBackIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(goBackIntent);
    }

    /**
     * Saving the page state in order to restore it after switching the apps or changing the app orientation
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    /**
     * Establishing the required connections to the services
     * and
     * Checking for the required permissions
     */
    @Override
    protected void onStart() {
        super.onStart();
        showStatus(false); //Shows the loading animations
        bindSpeechService();
        // Start listening to voices
    }

    @Override
    protected void startSpeechDependentService() {
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecorder();
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.RECORD_AUDIO)) {
            showPermissionMessageDialog();
        } else {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        }
        if(!isIceBroken) {
            responseServer.respond();
            isIceBroken = true;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Stop listening to voice
        stopVoiceRecorder();
        // Unbind Services
        unBindSpeechService();
    }

//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        unBindSpeechService();
//    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    protected SpeechActivity getActivity() {
        return ExpTwoGroupOneActivity.this;
    }

    @Override
    protected String getResponseServerUrl() {
        return responseServerUrl;
    }

    @Override
    protected void finalizedRecognizedText(String text) {
        recognizedText.setText(text);
        recognizedTextBuffer.add(text);
        responseServer.setReceivedMessage(recognizedTextBuffer);
        responseServer.respond();
    }

    @Override
    protected void unFinalizedRecognizedText(String text) {
        recognizedText.setText(text);
    }

    @Override
    public void goToQuestions(){
        startActivity(new Intent(ExpTwoGroupOneActivity.this, PostTestActivity.class));
    }

    @Override
    protected void saveUtterance(String utterance) {

    }

    public void runCommand(int commandCode, int fulfillment, String responseParameter, String nextCommandHintText, boolean hasTriedAllCommands, boolean commandCompleted){
        showStatus(false);
        hintText.setText(nextCommandHintText);
        if(hasTriedAllCommands){nextButton.setVisibility(View.VISIBLE);}
        switch (commandCode) {
            // do nothing if something is already on/off or set | or just do anything we are asked
            case 100:
                // Turn on the lights;
                lightsSwitch.setChecked(true);
                if (toastMessage!= null) toastMessage.cancel();
                toastMessage = Toast.makeText(ExpTwoGroupOneActivity.this, "Lights are on", Toast.LENGTH_SHORT);
                toastMessage.show();
                break;
            case 101:
                // Turn off the lights
                lightsSwitch.setChecked(false);
                if (toastMessage!= null) toastMessage.cancel();
                toastMessage = Toast.makeText(ExpTwoGroupOneActivity.this, "Lights are off", Toast.LENGTH_SHORT);
                toastMessage.show();
                break;
            case 200:
                // Turn on AC;
                acSwitch.setChecked(true);
                if (toastMessage!= null) toastMessage.cancel();
                toastMessage = Toast.makeText(ExpTwoGroupOneActivity.this, "AC is on", Toast.LENGTH_SHORT);
                toastMessage.show();
                break;
            case 201:
                // Turn off AC;
                acSwitch.setChecked(false);
                if (toastMessage!= null) toastMessage.cancel();
                toastMessage = Toast.makeText(ExpTwoGroupOneActivity.this, "AC is off", Toast.LENGTH_SHORT);
                toastMessage.show();
                break;
            case 300:
                // Set the temp to X degrees;
                acTemperature.setProgress(Integer.parseInt(responseParameter));
                if (toastMessage!= null) toastMessage.cancel();
                String message = "Temperature is set to: " + responseParameter + "°F";
                toastMessage = Toast.makeText(ExpTwoGroupOneActivity.this, message, Toast.LENGTH_SHORT);
                toastMessage.show();
                break;
            case 400:
                // Set the alarm
                time.setText(responseParameter);
                if (toastMessage!= null) toastMessage.cancel();
                String alarmMessage = "Alarm is set for: " + responseParameter;
                toastMessage = Toast.makeText(ExpTwoGroupOneActivity.this, alarmMessage, Toast.LENGTH_SHORT);
                toastMessage.show();
                break;
            default:
                break;
        }
    }

    protected void showListeningState(boolean hearingVoice){
        microphoneIcon.setImageResource(hearingVoice ? R.drawable.ic_mic_black_48dp : R.drawable.ic_mic_none_black_48dp);
        if(recognizedText.getText().equals("") || recognizedText.getText().equals(TALKING) || recognizedText.getText().equals(WAITING_FOR_SERVER)) {
            recognizedText.setText(LISTENING_MESSAGE);
        }
        spinner.setVisibility(View.GONE);
        loadingWhiteTransparent.setVisibility(View.GONE);
    }

    protected void showTalkingState() {
        microphoneIcon.setImageResource(R.drawable.chat);
        recognizedText.setText(TALKING);
        spinner.setVisibility(View.GONE);
        loadingWhiteTransparent.setVisibility(View.GONE);
    }

    @Override
    protected void showThinkingState() {

    }

    protected void showLoadingState() {
        microphoneIcon.setImageResource(R.drawable.block_microphone);
        recognizedText.setText(WAITING_FOR_SERVER);
        spinner.setVisibility(View.VISIBLE);
        loadingWhiteTransparent.setVisibility(View.VISIBLE);
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @SuppressLint("StaticFieldLeak")
    private class FetchResponse extends AsyncTask<String, Integer, String> {

        @Override
        protected String doInBackground(String... strings) {
            StringBuilder result = new StringBuilder();
            try {
                String urlParameters = strings[0];
//                Log.d("urlParameters", urlParameters);
                URL url = new URL("http://amandabot.xyz/conversation_finished/");
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
//                Log.d("Location", "HTTP STATUS: " + String.valueOf(status));
                InputStream inputStream = conn.getInputStream();
                BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                for (int c; (c = in.read()) >= 0;)
                    result.append((char)c);
            } catch (IOException e) {
                Log.d("Location", "IOException");
                e.printStackTrace();
            }

            return result.toString();
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            try {
                JSONObject resultJSON = new JSONObject(result);
//                Log.d("ServerStat", "Created the JSON Obj");
                JSONObject response = resultJSON.getJSONObject("response");
//                Log.d("ServerStat", "Got response");
                boolean success = response.getBoolean("success");
                Log.d("ServerStat", "Got success");
                if (success) {
                    goToQuestions();
                } else {
                    Toast.makeText(ExpTwoGroupOneActivity.this, "Server error!\nPlease try again later!", Toast.LENGTH_LONG).show();
                }
            } catch (JSONException e) {
                e.printStackTrace();
                Log.d("ServerStat", e.toString());
                Toast.makeText(ExpTwoGroupOneActivity.this, "Server error!\nPlease try again later!!!", Toast.LENGTH_LONG).show();
            }

        }
    }


}
