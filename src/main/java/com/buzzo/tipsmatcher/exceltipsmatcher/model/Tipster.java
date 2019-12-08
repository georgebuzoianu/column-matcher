package com.buzzo.tipsmatcher.exceltipsmatcher.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
@Builder
public class Tipster {
    String name;
    Map<Integer, String> results;
    Integer winsPerMonth;
    Integer previousWins;
    Integer totalWins;
}
