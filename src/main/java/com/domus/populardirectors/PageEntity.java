package com.domus.populardirectors;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class PageEntity implements Serializable {

    @Id @Setter private Integer id; // a.k.a pageNum

    /// /////////// REPEATED data in every page
    private int perPage;
    private int totalMovies;
    private int totalPages;
    /// //////////

    /**
     *    have raw JSON string written... and, maybe, we'll latter
     * have something like PageDTO here instead of String
     */
    @OneToMany(mappedBy = "page", cascade = CascadeType.ALL, orphanRemoval = true)
    Set<MovieEntity> movieEntitySet = new LinkedHashSet<>();

    public PageEntity(PageDTO firstPage) {
        this.id = firstPage.getPage();
        this.perPage = firstPage.getPerPage();
        this.totalPages = firstPage.getTotalPages();
        this.totalMovies = firstPage.getTotal();
        this.movieEntitySet.addAll(firstPage.getData().stream().map(MovieEntity::new).toList());
    }
}
