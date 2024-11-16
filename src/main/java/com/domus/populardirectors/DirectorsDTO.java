package com.domus.populardirectors;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DirectorsDTO
{
    private List<String> directors = new ArrayList<>();
}
