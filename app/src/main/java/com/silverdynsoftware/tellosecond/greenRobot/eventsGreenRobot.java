package com.silverdynsoftware.tellosecond.greenRobot;

public class eventsGreenRobot {
    public static class lastReceivedMessage {
        public final String lastMessage;
        public lastReceivedMessage(String lastMessage) {
            this.lastMessage = lastMessage;
        }
    }

    public static class lastReceivedStatus {
        public final String lastStatus;
        public lastReceivedStatus(String lastStatus) {
            this.lastStatus = lastStatus;
        }
    }

    public static class StreamOnReceivedMessage {
        public final String streamMessage;
        public StreamOnReceivedMessage(String streamMessage) {
            this.streamMessage = streamMessage;
        }
    }
}
