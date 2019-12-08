package com.buzzo.tipsmatcher.exceltipsmatcher.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@Builder
public class TipstersCombination {
    List<Tipster> tipsters;
    Map<Integer, Integer> winDays;

}
