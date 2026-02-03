package com.example.springboot.utilities;

import org.slf4j.Logger;

import java.time.LocalDate;

import com.example.springboot.models.WeightEntry;
import com.example.springboot.models.Centile;
import com.example.springboot.models.Point;

import java.util.List;

public class NormalDistributionUtility {
    private static final LocalDate DATE_OF_39_WEEKS_GESTATION = LocalDate.of(2026, 1, 12);
    private static final LocalDate DATE_OF_40_WEEKS_GESTATION = LocalDate.of(2026, 1, 19);

    public static double calculateStandardNormalDistribution(double z) {
        return (1/Math.sqrt(2 * Math.PI)) * Math.exp(-0.5 * z * z);
    }

    public static double calculateTrapeziumArea(double a, double b, double height) {
        return 0.5 * (a + b) * height;
    }

    public static Centile calculateCentile(WeightEntry weight, Logger log) {
        double zScore;
        if (dateToWeeksGestation(weight.getDate()) < 43.0) {
            log.info("Calculating z-score for weight entry before 43 weeks gestation");
            double gestationInWeeks = dateToWeeksGestation(weight.getDate());
            zScore = calculateZBefore43Weeks(weight.getWeightInGrams(), gestationInWeeks, log);
        } else {
            log.info("Calculating z-score for weight entry after 43 weeks gestation");
            double ageInYears = dateToYearsOld(weight.getDate());
            zScore = calculateZAfter43Weeks(weight.getWeightInGrams(), ageInYears, log);
        }
        
        double totalArea = 0.0;
        double lowerBound = -5.0;
        double stepSize = 0.01;
        double diff = zScore - lowerBound;
        int steps = (int) (diff / stepSize);
        double lastStep = lowerBound;

        for (int i = 0; i < steps; i++) {
            double x1 = lowerBound + i * stepSize;
            double x2 = lowerBound + (i + 1) * stepSize;
            if (x2 > zScore) {
                break;
            }
            double y1 = calculateStandardNormalDistribution(x1);
            double y2 = calculateStandardNormalDistribution(x2);
            totalArea += calculateTrapeziumArea(y1, y2, stepSize);
            lastStep += stepSize;
        }

        double y1 = calculateStandardNormalDistribution(lastStep);
        double y2 = calculateStandardNormalDistribution(zScore);
        totalArea += calculateTrapeziumArea(y1, y2, zScore - lastStep);

        log.info("Calculated centile for date {} and weight {}g: zScore = {}, centile = {}",
                weight.getDate(), weight.getWeightInGrams(), zScore, totalArea*100);
        return new Centile(weight.getDate(), weight.getWeightInGrams(), totalArea*100);
    }

    private static double calculateLBefore43Weeks(double gestationInWeeks) {
        return linearInterpolateToFindDependantVariable(CentileParametersData.PRE_43_WEEKS_L_VALUES, gestationInWeeks);
    }

    private static double calculateMBefore43Weeks(double gestationInWeeks) {
        return linearInterpolateToFindDependantVariable(CentileParametersData.PRE_43_WEEKS_M_VALUES, gestationInWeeks);
    }

    private static double calculateSBefore43Weeks(double gestationInWeeks) {
        return linearInterpolateToFindDependantVariable(CentileParametersData.PRE_43_WEEKS_S_VALUES, gestationInWeeks);
    }

    private static double calculateZBefore43Weeks(int weightInGrams, double gestationInWeeks, Logger log) {
        double l = calculateLBefore43Weeks(gestationInWeeks);
        double m = calculateMBefore43Weeks(gestationInWeeks);
        double s = calculateSBefore43Weeks(gestationInWeeks);
        log.info("L: {}, M: {}, S: {}", l, m, s);
        return calculateZ(weightInGrams, l, m, s);
    }

    private static double calculateLAfter43Weeks(double ageInYears) {
        return linearInterpolateToFindDependantVariable(CentileParametersData.WEEKS_43_TO_1_YEAR_L_VALUES, ageInYears);
    }

    private static double calculateMAfter43Weeks(double ageInYears) {
        return linearInterpolateToFindDependantVariable(CentileParametersData.WEEKS_43_TO_1_YEAR_M_VALUES, ageInYears);
    }

    private static double calculateSAfter43Weeks(double ageInYears) {
        return linearInterpolateToFindDependantVariable(CentileParametersData.WEEKS_43_TO_1_YEAR_S_VALUES, ageInYears);
    }

    private static double calculateZAfter43Weeks(int weightInGrams, double ageInYears, Logger log) {
        double l = calculateLAfter43Weeks(ageInYears);
        double m = calculateMAfter43Weeks(ageInYears);
        double s = calculateSAfter43Weeks(ageInYears);
        log.info("L: {}, M: {}, S: {}", l, m, s);
        return calculateZ(weightInGrams, l, m, s);
    }

    private static double calculateZ(int weightInGrams, double l, double m, double s) {
        return (Math.pow(((weightInGrams/1000.0) / m), l) - 1) / (l * s);
    }

    private static double dateToWeeksGestation(LocalDate measurementDate) {
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(DATE_OF_39_WEEKS_GESTATION, measurementDate);
        return (daysBetween / 7.0) + 39.0;
    }

    private static double dateToYearsOld(LocalDate measurementDate) {
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(DATE_OF_40_WEEKS_GESTATION, measurementDate);
        return daysBetween / 365.0;
    }

    private static double linearInterpolateToFindDependantVariable(List<Point> points, double targetX) {
        List<Point> adjacentPoints = binarySearchToFindAdjacentPoints(points, targetX);
        Point point1 = adjacentPoints.get(0);
        Point point2 = adjacentPoints.get(1);

        double slope = (point2.y() - point1.y()) / (point2.x() - point1.x());
        return point1.y() + slope * (targetX - point1.x());
    }

    private static List<Point> binarySearchToFindAdjacentPoints(List<Point> points, double targetX) {
        int left = 0;
        int right = points.size() - 1;

        while (right > left + 1) {
            int mid = Math.floorDiv(left + right, 2); 
            if (targetX > points.get(mid).x())
            {
                left = mid;
            }
            else
            {
                right = mid;
            }
        }

        return List.of(points.get(left), points.get(right));
    }
}