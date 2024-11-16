package com.domus.populardirectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PageDTO {
    private int page;
    @JsonProperty("per_page") private int perPage;
    private int total;
    @JsonProperty("total_pages") private int totalPages;

    List<MovieDTO> data = new ArrayList<>();
}
