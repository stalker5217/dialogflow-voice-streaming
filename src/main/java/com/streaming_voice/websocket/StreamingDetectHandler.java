package com.streaming_voice.websocket;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.BidiStream;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.dialogflow.v2.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.ByteString;
import com.google.protobuf.util.JsonFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StreamingDetectHandler extends BinaryWebSocketHandler {
    private final Logger logger = LoggerFactory.getLogger(this.getClass().getSimpleName());
    private final Map<WebSocketSession, BidiStream> bidiStreamMap = Collections.synchronizedMap(new HashMap<>());

    @Override
    public void afterConnectionEstablished(WebSocketSession socketSession) throws Exception {
        logger.info(String.format("[%s] Connection Establish Start", socketSession.getId()));

        super.afterConnectionEstablished(socketSession);

        // Read Json by InputStream
        ClassPathResource cpr = new ClassPathResource("dialogflow_credentials.json");
        GoogleCredentials credentials = GoogleCredentials.fromStream(cpr.getInputStream());
        String projectId = ((ServiceAccountCredentials)credentials).getProjectId();
        String dialogflowSessionId = UUID.randomUUID().toString();

        // Build Session Settings
        SessionsSettings.Builder settingsBuilder = SessionsSettings.newBuilder();
        SessionsSettings sessionsSettings = settingsBuilder.setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build();

        // Instantiates a client
        SessionsClient sessionsClient = SessionsClient.create(sessionsSettings);

        // Set the session name using the sessionId (UUID) and projectID (my-project-id)
        SessionName dialogflowSession = SessionName.of(projectId, dialogflowSessionId);

        // Instructs the speech recognizer how to process the audio content.
        // Note: hard coding audioEncoding and sampleRateHertz for simplicity.
        // Audio encoding of the audio content sent in the query request.
        InputAudioConfig inputAudioConfig =
                InputAudioConfig.newBuilder()
                        .setAudioEncoding(AudioEncoding.AUDIO_ENCODING_LINEAR_16)
                        .setLanguageCode("en-US") // languageCode = "en-US"
                        .setSampleRateHertz(16000) // sampleRateHertz = 16000
                        .build();

        // Build the query with the InputAudioConfig
        QueryInput queryInput = QueryInput.newBuilder().setAudioConfig(inputAudioConfig).build();

        // Create the Bidirectional stream
        BidiStream<StreamingDetectIntentRequest, StreamingDetectIntentResponse> bidiStream =
                sessionsClient.streamingDetectIntentCallable().call();

        // The first request must **only** contain the audio configuration:
        bidiStream.send(
                StreamingDetectIntentRequest.newBuilder()
                        .setSession(dialogflowSession.toString())
                        .setQueryInput(queryInput)
                        .build());

        bidiStreamMap.put(socketSession, bidiStream);

        logger.info(String.format("[%s] Connection Establish End", socketSession.getId()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession socketSession, CloseStatus status) throws Exception {
        logger.info(String.format("[%s] Connection Close Start", socketSession.getId()));

        super.afterConnectionClosed(socketSession, status);
        bidiStreamMap.remove(socketSession);

        logger.info(String.format("[%s] Connection Close End", socketSession.getId()));
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession socketSession, BinaryMessage message) throws Exception {
        logger.info(String.format("[%s] Handle Binary Message Start", socketSession.getId()));

        if(message.getPayloadLength() == 3){
            String msg = new String(message.getPayload().array());
            closeStream(socketSession);
        }
        else{
            BidiStream<StreamingDetectIntentRequest, StreamingDetectIntentResponse> bidiStream
                    = bidiStreamMap.get(socketSession);

            bidiStream.send(StreamingDetectIntentRequest.newBuilder()
                    .setInputAudio(ByteString.copyFrom(message.getPayload().array()))
                    .build());
        }

        logger.info(String.format("[%s] Handle Binary Message End", socketSession.getId()));
    }

    private void closeStream(WebSocketSession socketSession) throws Exception{
        BidiStream<StreamingDetectIntentRequest, StreamingDetectIntentResponse> bidiStream
                = bidiStreamMap.get(socketSession);

        // Tell the service you are done sending data
        bidiStream.closeSend();

        StreamingDetectIntentResponse lastResponse = null;
        for (StreamingDetectIntentResponse response : bidiStream) {
            lastResponse = response;

            QueryResult queryResult = response.getQueryResult();
            logger.info("====================");
            logger.info(String.format("TransScript: '%s'", response.getRecognitionResult().getTranscript()));
            logger.info(String.format("Intent Display Name: '%s'", queryResult.getIntent().getDisplayName()));
            logger.info(String.format("Query Text: '%s'", queryResult.getQueryText()));
            logger.info(String.format("Detected Intent: %s (confidence: %f)", queryResult.getIntent().getDisplayName(), queryResult.getIntentDetectionConfidence()));
            logger.info(String.format("Fulfillment Text: '%s'", queryResult.getFulfillmentText()));
        }

        if(lastResponse != null){
            StringBuilder sb = new StringBuilder();
            JsonFormat.printer().appendTo(lastResponse, sb);

            JsonParser jsonParser = new JsonParser();
            JsonObject jsonObj = (JsonObject) jsonParser.parse(sb.toString());

            socketSession.sendMessage(new TextMessage(jsonObj.toString()));
        }
        else{
            socketSession.sendMessage(new TextMessage("Response is Empty"));
        }

        socketSession.close();
    }
}