package com.zachRoot;

import ij.ImagePlus;
import ij.measure.CurveFitter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

public class XYPositioning {
	
	public static double[] getBeadCenter(ImagePlus img, ImageProcessor ip) {
		// X,Y COM Center Coordinates
		int[] xyCord = getCenterOfMass(img, ip);
		
		// Sub Pixel Localization utilizing cross correlation
		double[] xyCordSubPixel = xyRowColConvolve(ip, xyCord[0], xyCord[1]);
		
		return xyCordSubPixel;
	}
	
	private static int[] getCenterOfMass(ImagePlus img, ImageProcessor ip) {
		int[] xyCord = null;
		//TO DO CHECK IF THIS MAKES SENSE?
		int type = img.getType();
		
		if (type == ImagePlus.GRAY32)
			xyCord = fitBeadByCenterOfMass(img, (float[])ip.getPixels());
		else if (type == ImagePlus.GRAY8 || type == ImagePlus.GRAY16)
			xyCord = fitBeadByCenterOfMass(img, (float[])ip.convertToFloatProcessor().getPixels());
		else {
			throw new RuntimeException("Image Type Not Supported");
		}
		
		return xyCord;
	}
	

	
	
	/* This function is a java version of this matlab code:
	 * 
	 * function [x, y] = fitBeadByCenterOfMass(S)
		% Note: Faster on cpu until about size(S)=[100,100,100] class(s)=double.
		width = size(S,1);
		T = abs(S - sum(S, [1,2])/(width*width)); % mean is much much faster than median on very large images
		T_sum = sum(T, [1,2]);
		T_sum(T_sum==0) = eps;
		lin = (1:width);
		x = squeeze(sum(lin.*T, [1,2]) ./ T_sum);
		y = squeeze(sum(lin'.*T, [1,2]) ./ T_sum);
	 */
	
	
	private static int[] fitBeadByCenterOfMass(ImagePlus img, float[] flattenedImage) {
		
    	int width = img.getWidth();
    	// Square Image. Kept for readability
    	int height = width;
    	
        float[][] T = new float[width][height];
        
       
        // Calculate absolute differences
        float mean = calculateMean(flattenedImage);
        
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
            	T[i][j] = Math.abs(flattenedImage[i * width + j] - mean);
            }
        }
        
        float T_sum = sum(T);
      
        if (T_sum == 0) {
            T_sum = Float.MIN_VALUE;
        }
        
        // Generate linear index array
        float[] lin = new float[width];
        for (int i = 0; i < width; i++) {
            lin[i] = i + 1;
        }
        
        float xSum = 0;
    	float ySum = 0;
        for (int i = 0; i < width; i++) {	
        	for (int j = 0; j < height; j++) {
        		xSum += lin[j]*T[i][j];
        		ySum += lin[i]*T[i][j];
        	}
        }
        float x = xSum / T_sum;
        float y = ySum / T_sum;
        
        return new int[] { (int)x, (int)y };
    }

    
    public static float calculateMean(float[] flattenedImage) {
    	
        float sum = 0;
        for (float pixel : flattenedImage) {
            sum += pixel;
        }
        return sum / flattenedImage.length;
    }
    
    private static float sum(float[][] img) {
    	float sum = 0;
    	for(float[] col: img) {
    		for(float num: col) {
    			sum += num;
    		}
    	}
    	return sum;
    }
    
 // Sub Pixel Localization 
 	private static double[] xyRowColConvolve(ImageProcessor ip, int xPos, int yPos) {
 		
 		// Create local width to not impact other threads
 		int width = ip.getWidth();
 		// Width-1 to ensure an odd number sized kernel required by ImageProcessor.convolve();
 		if(width % 2 == 0) {width = width-1;}
 		 
 		// Create 2d Image of row Slice
 		int[] xAxisSignal = new int[width];
 		int[] yAxisSignal = new int[width]; 
 		ip.getRow(0, yPos, xAxisSignal, width);
 		ip.getColumn(xPos, 0, yAxisSignal, width);
 		
 		// Subtract mean
 		xAxisSignal = subtractMean(xAxisSignal);
 		yAxisSignal = subtractMean(yAxisSignal);
 		
 		// Reverse
 		float[] reverseXAxisSignal = new float[width];
 		float[] reverseYAxisSignal = new float[width];
 		for (int i=0; i<width; i++) {
 			reverseXAxisSignal[i] = xAxisSignal[width-i-1];
 			reverseYAxisSignal[i] = yAxisSignal[width-i-1];
 		}
 		
 		// Create FloatProcessors for Convolution 
 		FloatProcessor xSignal = new FloatProcessor(width, 1, xAxisSignal);
 		FloatProcessor ySignal = new FloatProcessor(width, 1, yAxisSignal);
 		
 		
 		// Convolve with reverseXYAxisSignal as kernel
 		xSignal.convolve(reverseXAxisSignal, width, 1);
 		ySignal.convolve(reverseYAxisSignal, width, 1);
 		
 		
 		float xMax = Float.MIN_VALUE;
 		float yMax = Float.MIN_VALUE;
 		int xMaxIndex = -1;
 		int yMaxIndex = -1;
 		
 		
 		for (int i = 0; i<width; i++) {	
 			if(xSignal.getf(i) > xMax) {
 				xMax = xSignal.getf(i);
 				xMaxIndex = i;
 			}
 			if(ySignal.getf(i) > yMax) {
 				yMax = ySignal.getf(i);
 				yMaxIndex = i;
 			}
 		}
 		
 		// 7 Point Curve fit
 		
 		// makes sure to not go out of bounds
 		int xleftBound = (xMaxIndex-3 < 0) ? 0 : xMaxIndex-3;
 		int xRightBound = (xMaxIndex+3 > width-1) ? width-1 : xMaxIndex+3;
 		int yleftBound = (yMaxIndex-3 < 0) ? 0 : yMaxIndex-3;
 		int yRightBound = (yMaxIndex+3 > width-1) ? width-1 : yMaxIndex+3;
 		
 		double[] xData = new double[7];
 		double[] xIndexes = new double[7];
 		double[] yData = new double[7];
 		double[] yIndexes = new double[7];
 		
 		
 		for(int i = 0; i < xRightBound-xleftBound+1; i++) {
 			xIndexes[i] = i+xleftBound;
 			xData[i] = xSignal.getf(i+xleftBound);
 		}
 		for(int i = 0; i < yRightBound-yleftBound+1; i++) {
 			yIndexes[i] = i+yleftBound;
 			yData[i] = ySignal.getf(i+yleftBound);
 		}
 		
 		
 		CurveFitter xSignalFit = new CurveFitter(xIndexes, xData);
 		xSignalFit.doFit(CurveFitter.POLY2);
 		
 		CurveFitter ySignalFit = new CurveFitter(yIndexes, yData);
 		ySignalFit.doFit(CurveFitter.POLY2);
 		
 		
 		// -b/2a for peak of curve
 		double xCord = -xSignalFit.getParams()[1]/(2*xSignalFit.getParams()[2]);
 		double yCord = -ySignalFit.getParams()[1]/(2*ySignalFit.getParams()[2]);
 		
 		// Fix Width after Convolution is complete
 		if(ip.getWidth() == width+1) {width = width + 1;}
 		
 		// Average position with center to adjust for Convolution algorithm
 		xCord = ((width)/2f-xCord)/2 + xCord;
  		yCord = ((width)/2f-yCord)/2 + yCord;
 		
 		
 		return new double[]{xCord,yCord};
 	}
 	
 // Perfectly fine that the return value doesn't have float precision
 	private static int[] subtractMean(int[] data) {
 		
 		int sum=0;
 		
 		for(int num: data) {
 			sum += num;
 		}
 		
 		double mean = sum/data.length;
 		for(int i = 0; i<data.length; i++) {
 			data[i]-= mean;
 		}
 		
 		return data;
 	}
 	
	
}
