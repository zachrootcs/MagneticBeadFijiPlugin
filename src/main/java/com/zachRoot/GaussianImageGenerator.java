package com.zachRoot;
import ij.ImagePlus;
import ij.process.FloatProcessor;

public class GaussianImageGenerator {

    public static FloatProcessor getGaussian(int centerX, int centerY) {
        int width = 256;  // Width of the image
        int height = 256; // Height of the image
        float sigma = 25.0f; // Standard deviation of the Gaussian

        // Generate Gaussian image
        float[][] gaussian = generateGaussian(width, height, centerX, centerY, sigma);

        // Convert 2D array to FloatProcessor
        FloatProcessor processor = new FloatProcessor(gaussian.length, gaussian[0].length);
        processor.setFloatArray(gaussian);

       return processor;
    }

    // Function to generate 2D Gaussian
    public static float[][] generateGaussian(int width, int height,int centerX, int centerY, float sigma) {
        float[][] gaussian = new float[width][height];
        float sigmaSquared = sigma * sigma;

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                float x = i - centerX;
                float y = j - centerY;
                float exponent = -(x * x + y * y) / (2 * sigmaSquared);
                gaussian[i][j] = (float) (Math.exp(exponent) / (2 * Math.PI * sigmaSquared));
            }
        }
        
        return gaussian;
    }
}
