## Dialogflow Streaming Bot  

Dialogflow는 음성 파일 기반의 intent detection을 지원합니다.  

한 가지 방법으로는 REST API 기반으로 파일을 던져서 감지하는 것, 
그리고 다른 방법으로는 gRPC 기반으로 스트림을 생성하여 감지하는 것입니다. 

후자의 방법으로 지연 시간을 줄이는 것을 목적으로 합니다. 
클라이언트는 웹 환경을 사용하며 브라우저에서 제공하는 음성 녹음 기능을 사용하여 웹 소켓을 통하여 전송합니다.

![Streaming_Detect_Intent](https://user-images.githubusercontent.com/51525202/97069650-94fd9f00-160c-11eb-8398-ad828767ff42.png)