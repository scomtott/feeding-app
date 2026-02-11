package com.example.springboot.utilities;

import org.slf4j.Logger;

public class LinearRegressionUtility {
    public static LinearRegressionResult performLinearRegression(long[] xValues, int[] yValues, Logger logger) {
        int n = xValues.length;
        if (n != yValues.length || n == 0) {
            throw new IllegalArgumentException("Input arrays must have the same non-zero length.");
        }

        double xMean = java.util.Arrays.stream(xValues).average().orElse(0.0);
        double yMean = java.util.Arrays.stream(yValues).average().orElse(0.0);

        double numerator = 0.0;
        double denominator = 0.0;
        for (int i = 0; i < n; i++) {
            numerator += (xValues[i] - xMean) * (yValues[i] - yMean);
            denominator += (xValues[i] - xMean) * (xValues[i] - xMean);
        }

        double slope = numerator / denominator;
        double intercept = yMean - slope * xMean;

        logger.info("Performed linear regression with slope: {} and intercept: {}", slope, intercept);

        return new LinearRegressionResult(slope, intercept);
    }
}