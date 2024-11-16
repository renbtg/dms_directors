package com.domus.populardirectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class MovieEntity implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition="BIGINT")
    private Long id; // TODO - should be uuid for long term uniqueness


    // ALL non-id columns can be null, just in case external move HTTP endpoint returns any of them as null

    private String title;
    @Column(name = "year_num") private String year; // year is a reserved word for H2 database
    private String rated;
    private String released;
    private String runtime;
    private String genre;
    private String director;
    private String writer;
    private String actors;

    @ManyToOne(targetEntity = PageEntity.class) @JoinColumn(name = "page_id", nullable = false)
    @Setter
    private PageEntity page;

    public MovieEntity(MovieDTO movieDTO) {
        this.id = null; // just to clarity it's null before saved

        this.director=movieDTO.getDirector();

        this.title=movieDTO.getTitle();
        this.year=movieDTO.getYear();
        this.rated=movieDTO.getRated();
        this.released=movieDTO.getReleased();
        this.runtime=movieDTO.getRuntime();
        this.genre=movieDTO.getGenre();
        this.writer=movieDTO.getWriter();
        this.actors=movieDTO.getActors();

    }
}
