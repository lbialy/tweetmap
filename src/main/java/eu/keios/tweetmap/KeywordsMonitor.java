package eu.keios.tweetmap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.rxjava.core.eventbus.EventBus;
import io.vertx.rxjava.core.eventbus.Message;
import io.vertx.rxjava.core.http.ServerWebSocket;
import io.vertx.rxjava.core.http.WebSocketFrame;
import javaslang.collection.HashMap;
import javaslang.collection.HashSet;
import javaslang.collection.Set;
import javaslang.control.Try;

import java.util.regex.Matcher;

/**
 * Copyright (c) 2016 Łukasz Biały
 *
 * @see <a href="https://keios.eu">https://keios.eu</a>
 */

class KeywordsMonitor {

    private HashMap<ServerWebSocket, Set<String>> observersWithKeywords;

    private HashMap<String, Integer> keywordCounts;

    private final EventBus eventBus;
    private final ObjectMapper objectMapper;

    KeywordsMonitor(EventBus eventBus, ObjectMapper objectMapper) {
        this.eventBus = eventBus;
        this.objectMapper = objectMapper;
        this.observersWithKeywords = HashMap.empty();
        this.keywordCounts = HashMap.empty();
    }

    void start() {
        eventBus.<String>consumer(EventBusAddresses.TWEETS_STREAM)
                .toObservable()
                .map(Message::body)
                .map(json -> Try.of(() -> objectMapper.readValue(json, eu.keios.tweetmap.ImmutableTweet.class)).get())
                .forEach(this::publish);
    }

    void connected(ServerWebSocket socket) {
        observersWithKeywords = observersWithKeywords.put(socket, HashSet.empty());
    }

    void disconnected(ServerWebSocket socket) {
        observersWithKeywords.get(socket).forEach(this::dropKeywords);
        observersWithKeywords = observersWithKeywords.remove(socket);

        propagateKeywordsChange();
    }

    void updateKeywordsForConnection(ServerWebSocket socket, Set<String> newKeywords) {
        Set<String> loweredNewKeywords = newKeywords.map(String::toLowerCase);
        Set<String> oldKeywords = observersWithKeywords.get(socket).getOrElse(HashSet.empty());

        Set<String> removedKeywords = oldKeywords.diff(loweredNewKeywords);
        Set<String> addedKeywords = loweredNewKeywords.diff(oldKeywords);

        dropKeywords(removedKeywords);

        addKeywords(addedKeywords);

        observersWithKeywords = observersWithKeywords.put(socket, newKeywords);

        propagateKeywordsChange();
    }

    private void publish(ImmutableTweet tweet) {
        try {

            String json = objectMapper.writeValueAsString(tweet);

            HashSet<String> words = HashSet.of(tweet.getText().split(" "))
                    .map(this::trimToToken)
                    .map(word -> word.startsWith("#") || word.startsWith("@") ? word.substring(1, word.length()) : word)
                    .map(String::toLowerCase);

            observersWithKeywords.forEach((socket, observedWords) -> {
                if (observedWords.intersect(words).nonEmpty()) {
                    socket.writeFrame(WebSocketFrame.textFrame(json, true));
                }
            });

        } catch (JsonProcessingException ex) {  // checked exceptions && (rx || stream) api == :(
            ex.printStackTrace();               // yep, just log it to stdout 4 now todo unbork with a logger
        }
    }

    private void propagateKeywordsChange() {
        try {
            ImmutableObservedKeywords observedKeywords = ImmutableObservedKeywords.builder().addAllKeywords(keywordCounts.keySet()).build();
            eventBus.publish(EventBusAddresses.KEYWORD_UPDATES_ADDRESS, objectMapper.writeValueAsString(observedKeywords));
        } catch (JsonProcessingException ex) {  // checked exceptions && (rx || stream) api == :(
            ex.printStackTrace();               // yep, just log it to stdout 4 now todo unbork with a logger
        }
    }

    private void addKeywords(Set<String> addedKeywords) {
        addedKeywords.forEach(keyword -> {
            Integer currentKeywordCount = keywordCounts.get(keyword).getOrElse(0);
            Integer countAfterAddition = currentKeywordCount + 1;

            keywordCounts = keywordCounts.put(keyword, countAfterAddition);
        });
    }

    private void dropKeywords(Set<String> removedKeywords) {
        removedKeywords.forEach(keyword -> {
            Integer currentKeywordCount = keywordCounts.get(keyword).getOrElse(0);
            Integer countAfterRemoval = currentKeywordCount - 1;

            if (countAfterRemoval <= 0) {
                keywordCounts = keywordCounts.remove(keyword);
            } else {
                keywordCounts = keywordCounts.put(keyword, countAfterRemoval);
            }
        });
    }

    private String trimToToken(String string) { // poor man's tokenizer :(
        String characters = Matcher.quoteReplacement("(:;.,)");
        String regex = "^[" + characters + "]+|[" + characters + "]+$";
        return string.replaceAll(regex, "");
    }
}
