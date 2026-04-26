package com.example.springboot.utilities;

import java.util.List;

import com.example.springboot.models.Point;

public final class LengthCentileParametersData implements CentileLmsDataProvider {
    public static final LengthCentileParametersData INSTANCE = new LengthCentileParametersData();

    // x values in weeks - L value is constant at 1.0 regardless of gestation
    public static final List<Point> PRE_43_WEEKS_L_VALUES = List.of(
        new Point(23, 1.0),
        new Point(24, 1.0),
        new Point(25, 1.0),
        new Point(26, 1.0),
        new Point(27, 1.0),
        new Point(28, 1.0),
        new Point(29, 1.0),
        new Point(30, 1.0),
        new Point(31, 1.0),
        new Point(32, 1.0),
        new Point(33, 1.0),
        new Point(34, 1.0),
        new Point(35, 1.0),
        new Point(36, 1.0),
        new Point(37, 1.0),
        new Point(38, 1.0),
        new Point(39, 1.0),
        new Point(40, 1.0),
        new Point(41, 1.0),
        new Point(42, 1.0),
        new Point(43, 1.0)
    );

    // x values in weeks - M values for pre 43 weeks
    public static final List<Point> PRE_43_WEEKS_M_VALUES = List.of(
        new Point(25, 34.59544),
        new Point(26, 35.59771),
        new Point(27, 36.60905),
        new Point(28, 37.65832),
        new Point(29, 38.76987),
        new Point(30, 39.94117),
        new Point(31, 41.14154),
        new Point(32, 42.34725),
        new Point(33, 43.538),
        new Point(34, 44.69314),
        new Point(35, 45.79079),
        new Point(36, 46.81071),
        new Point(37, 47.73972),
        new Point(38, 48.57771),
        new Point(39, 49.33962),
        new Point(40, 50.01719),
        new Point(41, 50.62523),
        new Point(42, 51.20649)
    );

    // x values in weeks - S values for pre 43 weeks
    public static final List<Point> PRE_43_WEEKS_S_VALUES = List.of(
        new Point(25, 0.08086044),
        new Point(26, 0.07735533),
        new Point(27, 0.07386597),
        new Point(28, 0.07042367),
        new Point(29, 0.06701891),
        new Point(30, 0.06362674),
        new Point(31, 0.06025431),
        new Point(32, 0.0569387),
        new Point(33, 0.05372271),
        new Point(34, 0.05064634),
        new Point(35, 0.04773628),
        new Point(36, 0.04500635),
        new Point(37, 0.04248754),
        new Point(38, 0.04026448),
        new Point(39, 0.03839778),
        new Point(40, 0.0369674),
        new Point(41, 0.03608866),
        new Point(42, 0.03570984)
    );

    // x values in years - L value is constant at 1.0 regardless of gestation
    public static final List<Point> WEEKS_43_TO_1_YEAR_L_VALUES = List.of(
        new Point(0.038329911, 1.0),
        new Point(0.057494867, 1.0),
        new Point(0.076659822, 1.0),
        new Point(0.083333333, 1.0),
        new Point(0.095824778, 1.0),
        new Point(0.114989733, 1.0),
        new Point(0.134154689, 1.0),
        new Point(0.153319644, 1.0),
        new Point(0.166666667, 1.0),
        new Point(0.1724846, 1.0),
        new Point(0.191649555, 1.0),
        new Point(0.210814511, 1.0),
        new Point(0.229979466, 1.0),
        new Point(0.249144422, 1.0),
        new Point(0.25, 1.0),
        new Point(0.333333333, 1.0),
        new Point(0.416666667, 1.0),
        new Point(0.5, 1.0),
        new Point(0.583333333, 1.0),
        new Point(0.666666667, 1.0),
        new Point(0.75, 1.0),
        new Point(0.833333333, 1.0),
        new Point(0.916666667, 1.0),
        new Point(1, 1.0)
    );

    // x values in years - M values for post 43 weeks
    public static final List<Point> WEEKS_43_TO_1_YEAR_M_VALUES = List.of(
        new Point(0.038329911, 51.512),
        new Point(0.057494867, 52.4695),
        new Point(0.076659822, 53.3809),
        new Point(0.083333333, 53.6872),
        new Point(0.095824778, 54.2454),
        new Point(0.114989733, 55.0642),
        new Point(0.134154689, 55.8406),
        new Point(0.153319644, 56.5767),
        new Point(0.166666667, 57.0673),
        new Point(0.1724846, 57.2761),
        new Point(0.191649555, 57.9436),
        new Point(0.210814511, 58.5816),
        new Point(0.229979466, 59.1922),
        new Point(0.249144422, 59.7773),
        new Point(0.25, 59.8029),
        new Point(0.333333333, 62.0899),
        new Point(0.416666667, 64.0301),
        new Point(0.5, 65.7311),
        new Point(0.583333333, 67.2873),
        new Point(0.666666667, 68.7498),
        new Point(0.75, 70.1435),
        new Point(0.833333333, 71.4818),
        new Point(0.916666667, 72.771),
        new Point(1, 74.015)
    );

    // x values in years - S values for post 43 weeks
    public static final List<Point> WEEKS_43_TO_1_YEAR_S_VALUES = List.of(
        new Point(0.038329911, 0.03694),
        new Point(0.057494867, 0.03669),
        new Point(0.076659822, 0.03647),
        new Point(0.083333333, 0.0364),
        new Point(0.095824778, 0.03627),
        new Point(0.114989733, 0.03609),
        new Point(0.134154689, 0.03593),
        new Point(0.153319644, 0.03578),
        new Point(0.166666667, 0.03568),
        new Point(0.1724846, 0.03564),
        new Point(0.191649555, 0.03552),
        new Point(0.210814511, 0.0354),
        new Point(0.229979466, 0.0353),
        new Point(0.249144422, 0.0352),
        new Point(0.25, 0.0352),
        new Point(0.333333333, 0.03486),
        new Point(0.416666667, 0.03463),
        new Point(0.5, 0.03448),
        new Point(0.583333333, 0.03441),
        new Point(0.666666667, 0.0344),
        new Point(0.75, 0.03444),
        new Point(0.833333333, 0.03452),
        new Point(0.916666667, 0.03464),
        new Point(1, 0.03479)
    );

    private LengthCentileParametersData() {
    }

    @Override
    public List<Point> pre43WeeksLValues() {
        return PRE_43_WEEKS_L_VALUES;
    }

    @Override
    public List<Point> pre43WeeksMValues() {
        return PRE_43_WEEKS_M_VALUES;
    }

    @Override
    public List<Point> pre43WeeksSValues() {
        return PRE_43_WEEKS_S_VALUES;
    }

    @Override
    public List<Point> weeks43To1YearLValues() {
        return WEEKS_43_TO_1_YEAR_L_VALUES;
    }

    @Override
    public List<Point> weeks43To1YearMValues() {
        return WEEKS_43_TO_1_YEAR_M_VALUES;
    }

    @Override
    public List<Point> weeks43To1YearSValues() {
        return WEEKS_43_TO_1_YEAR_S_VALUES;
    }
}