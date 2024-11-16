package com.domus.populardirectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Data;

@Data
public class MovieDTO {
    @JsonProperty("Title") private String title;
    @JsonProperty("Year") private String year;
    @JsonProperty("Rated") private String rated;
    @JsonProperty("Released") private String released;
    @JsonProperty("Runtime") private String runtime;
    @JsonProperty("Genre") private String genre;
    @JsonProperty("Director") private String director;
    @JsonProperty("Writer") private String writer;
    @JsonProperty("Actors") private String actors;
}
