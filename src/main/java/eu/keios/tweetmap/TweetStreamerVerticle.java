package eu.keios.tweetmap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.eventbus.EventBus;
import io.vertx.rxjava.core.eventbus.Message;
import javaslang.control.Try;
import twitter4j.*;

/**
 * Copyright (c) 2016 Łukasz Biały
 *
 * @see <a href="https://keios.eu">https://keios.eu</a>
 */

public class TweetStreamerVerticle extends AbstractVerticle {

    private EventBus eventBus;

    private TwitterStream twitterStream;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void start() {
        objectMapper.registerModule(new Jdk8Module());

        eventBus = vertx.eventBus();

        eventBus.<String>consumer(EventBusAddresses.KEYWORD_UPDATES_ADDRESS)
                .toObservable()
                .map(Message::body)
                .map(json -> Try.of(() -> objectMapper.readValue(json, ImmutableObservedKeywords.class)).get()) // fugly, checked exceptions :/
                .subscribe(this::updateTwitterStreamFilter);

        makeStream();
    }

    private void updateTwitterStreamFilter(ImmutableObservedKeywords keywordsToTrack) {
        if (keywordsToTrack.keywords().isEmpty()) {
            System.out.println("No keywords to track, shutting down stream.");

            vertx.executeBlocking(
                    future -> {
                        twitterStream.shutdown();
                        future.complete(null);
                    },
                    res -> System.out.println("Stream was shut down correctly.")
            );

        } else {
            System.out.println("Tracking: " + String.join(", ", keywordsToTrack.keywords()));
            vertx.executeBlocking(
                    future -> {
                        twitterStream.filter(new FilterQuery(keywordsToTrack.keywords().toArray(new String[]{})));
                        future.complete(null);
                    },
                    res -> System.out.println("Stream filter updated.")
            );
        }
    }

    private void makeStream() {
        StatusListener listener = new StatusListener() {

            @Override
            public void onStatus(Status status) {
                ImmutableTweet tweet = ImmutableTweet.fromStatus(status);

                try {
                    String json = objectMapper.writeValueAsString(tweet);
                    eventBus.send(EventBusAddresses.TWEETS_STREAM, json);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
            }

            @Override
            public void onTrackLimitationNotice(int i) {
            }

            @Override
            public void onScrubGeo(long l, long l1) {
            }

            @Override
            public void onStallWarning(StallWarning stallWarning) {
            }

            @Override
            public void onException(Exception ex) {
                ex.printStackTrace();
            }
        };

        twitterStream = new TwitterStreamFactory().getInstance();
        twitterStream.addListener(listener);
    }
}
