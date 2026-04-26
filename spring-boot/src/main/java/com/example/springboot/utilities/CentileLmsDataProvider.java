package com.example.springboot.utilities;

import java.util.List;

import com.example.springboot.models.Point;

public interface CentileLmsDataProvider {
    List<Point> pre43WeeksLValues();

    List<Point> pre43WeeksMValues();

    List<Point> pre43WeeksSValues();

    List<Point> weeks43To1YearLValues();

    List<Point> weeks43To1YearMValues();

    List<Point> weeks43To1YearSValues();
}