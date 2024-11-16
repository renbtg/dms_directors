package com.domus.populardirectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class PageDTO {
    private int page;
    @JsonProperty("per_page") private int perPage;
    private int total;
    @JsonProperty("total_pages") private int totalPages;

    List<MovieDTO> data = new ArrayList<>();

    public PageDTO(PageEntity pageEntity) {
        this.page = pageEntity.getId();
        this.perPage = pageEntity.getPerPage();
        this.total = pageEntity.getTotalMovies();
        this.totalPages = pageEntity.getTotalPages();
        this.data = pageEntity.getMovieEntitySet().stream()
                .map(MovieDTO::new)
                .toList();
    }
}
