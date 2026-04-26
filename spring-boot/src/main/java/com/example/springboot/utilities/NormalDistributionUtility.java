package com.example.springboot.utilities;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;

import com.example.springboot.models.Point;

public class NormalDistributionUtility {
    private static final LocalDate DATE_OF_39_WEEKS_GESTATION = LocalDate.of(2026, 1, 12);
    private static final LocalDate DATE_OF_40_WEEKS_GESTATION = LocalDate.of(2026, 1, 19);

    public static double calculateStandardNormalDistribution(double z) {
        return (1/Math.sqrt(2 * Math.PI)) * Math.exp(-0.5 * z * z);
    }

    public static double calculateTrapeziumArea(double a, double b, double height) {
        return 0.5 * (a + b) * height;
    }

    public static double calculateCentileValue(
        double measurementValue,
        LocalDate measurementDate,
        CentileLmsDataProvider lmsDataProvider,
        String measurementName,
        double normalisationDivisor,
        Logger log
    ) {
        double zScore;
        if (dateToWeeksGestation(measurementDate) < 43.0) {
            log.info("Calculating z-score for {} entry before 43 weeks gestation", measurementName);
            double gestationInWeeks = dateToWeeksGestation(measurementDate);
            zScore = calculateZBefore43Weeks(measurementValue, gestationInWeeks, lmsDataProvider, normalisationDivisor, log);
        } else {
            log.info("Calculating z-score for {} entry after 43 weeks gestation", measurementName);
            double ageInYears = dateToYearsOld(measurementDate);
            zScore = calculateZAfter43Weeks(measurementValue, ageInYears, lmsDataProvider, normalisationDivisor, log);
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

        log.info("Calculated centile for date {} and {} {}: zScore = {}, centile = {}",
                measurementDate, measurementName, measurementValue, zScore, totalArea * 100);
        return totalArea * 100;
    }

    private static double calculateLBefore43Weeks(double gestationInWeeks, CentileLmsDataProvider lmsDataProvider) {
        return linearInterpolateToFindDependantVariable(lmsDataProvider.pre43WeeksLValues(), gestationInWeeks);
    }

    private static double calculateMBefore43Weeks(double gestationInWeeks, CentileLmsDataProvider lmsDataProvider) {
        return linearInterpolateToFindDependantVariable(lmsDataProvider.pre43WeeksMValues(), gestationInWeeks);
    }

    private static double calculateSBefore43Weeks(double gestationInWeeks, CentileLmsDataProvider lmsDataProvider) {
        return linearInterpolateToFindDependantVariable(lmsDataProvider.pre43WeeksSValues(), gestationInWeeks);
    }

    private static double calculateZBefore43Weeks(
        double measurementValue,
        double gestationInWeeks,
        CentileLmsDataProvider lmsDataProvider,
        double normalisationDivisor,
        Logger log
    ) {
        double l = calculateLBefore43Weeks(gestationInWeeks, lmsDataProvider);
        double m = calculateMBefore43Weeks(gestationInWeeks, lmsDataProvider);
        double s = calculateSBefore43Weeks(gestationInWeeks, lmsDataProvider);
        log.info("L: {}, M: {}, S: {}", l, m, s);
        return calculateZ(measurementValue, l, m, s, normalisationDivisor);
    }

    private static double calculateLAfter43Weeks(double ageInYears, CentileLmsDataProvider lmsDataProvider) {
        return linearInterpolateToFindDependantVariable(lmsDataProvider.weeks43To1YearLValues(), ageInYears);
    }

    private static double calculateMAfter43Weeks(double ageInYears, CentileLmsDataProvider lmsDataProvider) {
        return linearInterpolateToFindDependantVariable(lmsDataProvider.weeks43To1YearMValues(), ageInYears);
    }

    private static double calculateSAfter43Weeks(double ageInYears, CentileLmsDataProvider lmsDataProvider) {
        return linearInterpolateToFindDependantVariable(lmsDataProvider.weeks43To1YearSValues(), ageInYears);
    }

    private static double calculateZAfter43Weeks(
        double measurementValue,
        double ageInYears,
        CentileLmsDataProvider lmsDataProvider,
        double normalisationDivisor,
        Logger log
    ) {
        double l = calculateLAfter43Weeks(ageInYears, lmsDataProvider);
        double m = calculateMAfter43Weeks(ageInYears, lmsDataProvider);
        double s = calculateSAfter43Weeks(ageInYears, lmsDataProvider);
        log.info("L: {}, M: {}, S: {}", l, m, s);
        return calculateZ(measurementValue, l, m, s, normalisationDivisor);
    }

    private static double calculateZ(double measurementValue, double l, double m, double s, double normalisationDivisor) {
        return (Math.pow(((measurementValue / normalisationDivisor) / m), l) - 1) / (l * s);
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
        if (points == null || points.size() < 2) {
            throw new IllegalStateException("LMS data must contain at least two points for interpolation.");
        }

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