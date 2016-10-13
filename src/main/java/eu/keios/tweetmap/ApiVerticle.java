package eu.keios.tweetmap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.eventbus.EventBus;
import io.vertx.rxjava.core.http.ServerWebSocket;
import io.vertx.rxjava.core.http.WebSocketFrame;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.handler.StaticHandler;
import javaslang.collection.HashSet;
import javaslang.control.Try;

import java.util.Optional;

import static javaslang.API.*;
import static javaslang.Patterns.Failure;
import static javaslang.Patterns.Success;


/**
 * Copyright (c) 2016 Łukasz Biały
 *
 * @see <a href="https://keios.eu">https://keios.eu</a>
 */

public class ApiVerticle extends AbstractVerticle {

    private final static String TWEET_STREAMER = "eu.keios.tweetmap.TweetStreamerVerticle";

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        objectMapper.registerModule(new Jdk8Module());

        vertx.deployVerticle(TWEET_STREAMER, res -> {
            if (res.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(res.cause());
            }
        });

        final WebSocketFrame invalidMessage = WebSocketFrame.textFrame(new JsonObject().put("error", "invalid message").toString(), true);

        final EventBus eventBus = vertx.eventBus();

        final Router router = Router.router(vertx);

        final KeywordsMonitor monitor = new KeywordsMonitor(eventBus, objectMapper);

        router.route("/ws").handler(routingContext -> {
            ServerWebSocket socket = routingContext.request().upgrade();
            System.out.println("Websocket connected.");

            monitor.connected(socket);

            socket.toObservable()
                    .map(buffer -> Try.of(() -> objectMapper.readValue(buffer.toString("UTF-8"), ImmutableObservedKeywords.class)))
                    .subscribe(
                            observedKeywordsTry -> Match(observedKeywordsTry).of(
                                    Case(Success($()), observedKeywords -> {
                                        monitor.updateKeywordsForConnection(socket, HashSet.ofAll(observedKeywords.keywords()));
                                        return Optional.empty();
                                    }),
                                    Case(Failure($()), x -> {
                                        socket.writeFrame(invalidMessage);
                                        return Optional.empty();
                                    })
                            ),
                            failure -> {
                                monitor.disconnected(socket);
                                System.out.println("Connection failed: " + failure.getMessage());
                            },
                            () -> {
                                monitor.disconnected(socket);
                                System.out.println("Connection closed.");
                            }
                    );
        });

        router.route().handler(StaticHandler.create());

        monitor.start();

        Try<Integer> tryPort = Try.of(() -> Integer.valueOf(System.getProperty("http.port")));

        Integer port = tryPort.getOrElse(8080);

        System.out.println("Starting application on port " + port + "...");

        vertx.createHttpServer().requestHandler(router::accept).listen(port);
    }

}
