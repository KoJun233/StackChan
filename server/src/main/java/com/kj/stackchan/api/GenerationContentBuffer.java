package com.kj.stackchan.api;

final class GenerationContentBuffer {

    private final StringBuilder content = new StringBuilder();

    synchronized void append(String text) {
        content.append(text);
    }

    synchronized String snapshot() {
        return content.toString();
    }
}
