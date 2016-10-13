package eu.keios.tweetmap;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;

/**
 * Copyright (c) 2016 Łukasz Biały
 *
 * @see <a href="https://keios.eu">https://keios.eu</a>
 */

@Value.Immutable
@JsonSerialize(as = ImmutableObservedKeywords.class)
@JsonDeserialize(as = ImmutableObservedKeywords.class)
interface ObservedKeywords {
    List<String> keywords();
}