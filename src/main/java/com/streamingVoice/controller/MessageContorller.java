package com.streamingVoice.controller;

import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
public class MessageContorller {
    @MessageMapping("/intent/stream")
    public String streamingDialogflow(Principal principal){
        System.out.println(principal.getName());

        return principal.getName();
    }
}
