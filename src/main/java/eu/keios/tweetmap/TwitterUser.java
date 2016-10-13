package eu.keios.tweetmap;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

/**
 * Copyright (c) 2016 Łukasz Biały
 *
 * @see <a href="https://keios.eu">https://keios.eu</a>
 */

@Value.Immutable
@JsonSerialize(as = ImmutableTwitterUser.class)
@JsonDeserialize(as = ImmutableTwitterUser.class)
interface TwitterUser {
    String id();
    String name();
    String screenName();
    String profileImageUrl();
}
