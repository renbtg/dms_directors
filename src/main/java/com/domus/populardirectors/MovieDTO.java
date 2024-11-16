package com.domus.populardirectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MovieDTO
{
    @JsonProperty("Title") private String title;
    @JsonProperty("Year") private String year;
    @JsonProperty("Rated") private String rated;
    @JsonProperty("Released") private String released;
    @JsonProperty("Runtime") private String runtime;
    @JsonProperty("Genre") private String genre;
    @JsonProperty("Director") private String director;
    @JsonProperty("Writer") private String writer;
    @JsonProperty("Actors") private String actors;

    public MovieDTO(MovieEntity movieEntity) {
        this.title = movieEntity.getTitle();
        this.year = movieEntity.getYear();
        this.rated = movieEntity.getRated();
        this.released = movieEntity.getReleased();
        this.runtime = movieEntity.getRuntime();
        this.genre = movieEntity.getGenre();
        this.director = movieEntity.getDirector();
        this.writer = movieEntity.getWriter();
        this.actors = movieEntity.getActors();
    }
}
