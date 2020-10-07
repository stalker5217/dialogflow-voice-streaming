package com.streaming_voice.websocket;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.BidiStream;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.dialogflow.v2.*;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StreamingDetectHandler extends BinaryWebSocketHandler {
    private final Logger logger = LoggerFactory.getLogger(this.getClass().getSimpleName());
    private final Map<WebSocketSession, BidiStream> bidiStreamMap = Collections.synchronizedMap(new HashMap<>());

    @Override
    public void afterConnectionEstablished(WebSocketSession socketSession) throws Exception {
        logger.info(socketSession.getId() + "Connection Establish Start");

        super.afterConnectionEstablished(socketSession);

        // Read Json by InputStream
        InputStream inputStream = new FileInputStream("");
        GoogleCredentials credentials = GoogleCredentials.fromStream(inputStream);
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

        logger.info(socketSession.getId() + "Connection Establish End");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession socketSession, CloseStatus status) throws Exception {
        logger.info(socketSession.getId() + "Connection Close Start");

        super.afterConnectionClosed(socketSession, status);

        BidiStream<StreamingDetectIntentRequest, StreamingDetectIntentResponse> bidiStream
                = bidiStreamMap.get(socketSession);

        // Tell the service you are done sending data
        bidiStream.closeSend();

        for (StreamingDetectIntentResponse response : bidiStream) {
            QueryResult queryResult = response.getQueryResult();
            logger.info("====================");
            logger.info("Intent Display Name: '{0}'", queryResult.getIntent().getDisplayName());
            logger.info("Query Text: '{0}'", queryResult.getQueryText());
            logger.info("Detected Intent: {0} (confidence: {1})", queryResult.getIntent().getDisplayName(), queryResult.getIntentDetectionConfidence());
            logger.info("Fulfillment Text: '{0}'", queryResult.getFulfillmentText());
        }

        logger.info(socketSession.getId() + "Connection Close End");
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession socketSession, BinaryMessage message) throws Exception {
        logger.info(String.format("{0} Handle Binary Message Start"), socketSession.getId());

        BidiStream<StreamingDetectIntentRequest, StreamingDetectIntentResponse> bidiStream
                = bidiStreamMap.get(socketSession);

        bidiStream.send(StreamingDetectIntentRequest.newBuilder()
                .setInputAudio(ByteString.copyFrom(message.getPayload().array()))
                .build());

        logger.info(String.format("{0} Handle Binary Message End"), socketSession.getId());
    }
}