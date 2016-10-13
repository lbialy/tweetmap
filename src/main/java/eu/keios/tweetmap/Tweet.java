package eu.keios.tweetmap;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;
import twitter4j.GeoLocation;
import twitter4j.Status;

import java.util.Date;
import java.util.Optional;

/**
 * Copyright (c) 2016 Łukasz Biały
 *
 * @see <a href="https://keios.eu">https://keios.eu</a>
 */

@Value.Immutable
@JsonSerialize(as = ImmutableTweet.class)
@JsonDeserialize(as = ImmutableTweet.class)
abstract class Tweet {
    abstract ImmutableTwitterUser getUser();

    abstract String getText();

    abstract Date getCreatedAt();

    abstract String getId();

    abstract Optional<GeoLocation> maybeGeoLocation();

    static ImmutableTweet fromStatus(Status status) {

        ImmutableTwitterUser user = ImmutableTwitterUser.builder()
                .name(status.getUser().getName())
                .screenName(status.getUser().getScreenName())
                .profileImageUrl(status.getUser().getProfileImageURL())
                .id(String.valueOf(status.getUser().getId()))
                .build();

        ImmutableTweet.Builder builder = ImmutableTweet.builder()
                .user(user)
                .text(status.getText())
                .id(String.valueOf(status.getId()))
                .createdAt(status.getCreatedAt());

        if (null != status.getGeoLocation()) {
            builder.maybeGeoLocation(status.getGeoLocation());
        }

        return builder.build();
    }
}