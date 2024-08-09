package com.zachRoot;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.CurveFitter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

public class ZPositioning {
	
	// Radius of the z radial profiles
	private static int radius;
	
	// Stores the zlut: a lookup table of radial profiles
	static float[][] zlut;
	
	// Corresponding z positions for the zlut
	static int[] zlutHeights;
	
	static final int nPointsQuadFit = 5;
	
	// Strings for UI text
	static final String UI_ZLUT_DIR_TITLE = "Choose directory for Z Positioning";
	static final String UI_ZLUT_DIR_MESSAGE = 
			"Select a directory for the references to create a ZLUT "
			+ "/nor where a previous ZLUT has already been created";
	
	
	public static void createZLut(ImagePlus img) {
		
		//make sure its even
		radius = img.getWidth()/3 - img.getWidth()%2;
		
		// Directory for ZLUT Reference Images
		String directory_path = Gui.getDirectoryFromUser(UI_ZLUT_DIR_TITLE, UI_ZLUT_DIR_MESSAGE);		
		
		// Checks to see if the zlut was previously saved in the folder then loads it into the zlut variable
		if(isZlutInFolder(directory_path)) {
			return;
		}
		
		// ZLut Component Images
		ArrayList<ComparableImagePlus> images = MagneticBead.getReferenceImages(directory_path);
		Collections.sort(images, ComparableImagePlus.Z_COMPARATOR);
		
		// Matrix with each index being the radial profile of image about center of bead
		zlut        = new float[images.size()][radius+1];
		zlutHeights = new int  [images.size()];
		
		// Loop through each image
		for(int i = 0; i<images.size(); i++) {
			ImageProcessor ip = images.get(i).getProcessor();
			
			double[] xyCordSubPixel = XYPositioning.getBeadCenter(img, ip);
			
			zlut[i] = createRadialProfile(xyCordSubPixel, ip);
			
			zlutHeights[i] = images.get(i).getZPos();
			
		}
		if(checkDuplicates(zlutHeights)) {
			IJ.showMessage("Warning: There exists duplicate radial profiles in this ZLUT. This can cause Z positioning to be inaccurate");
		}
		Gui.promptUserToSaveZLut(directory_path); 
	}
	
	
	private static boolean checkDuplicates(int[] arr) {
		for(int i = 0; i<arr.length-1; i++) {
			if(arr[i] == arr[i+1]) {
				return true;
			}
		}
		return false;
	}


	private static float[] createRadialProfile(double[] xyCord, ImageProcessor ip) {
		
		int width = ip.getWidth();
		// Array where index is distance from center and value is the number of times that distance was found
		int[] numRho = new int[radius+1];
		
		// Array where index is distance from center and value is the sum of the pixels of that distance
		float[] intensitySum = new float[radius+1];
		
		float[] pixels = (float[])ip.convertToFloatProcessor().getPixels(); 
		
		for(int x = 0; x < width; x++) {
			for(int y = 0; y < width; y++) {
				int rhoValue = (int)(Math.round(Math.sqrt((xyCord[0]-x)*(xyCord[0]-x) + (xyCord[1]-y)*(xyCord[1]-y))));
				
				// Set bounds
				if(rhoValue > radius) rhoValue = radius;
				// Sets to 1 because some profiles will have a 0 rhovalue while others do not. 
				//This causes problems when finding a close zlut
				if(rhoValue < 1)      rhoValue = 1;
				
				// Sum values of pixels
				intensitySum[rhoValue] += pixels[x+y*width];
				// increment counter
				numRho[rhoValue] +=1;
			}
		}
		
		//Divide intensitysum by numrho to get the average radial profile
		float[] radialProfile = new float[radius+1];
		for(int i = 0; i < radius+1; i++) {
			radialProfile[i] = intensitySum[i]/numRho[i];
			if (Double.isNaN(radialProfile[i])) radialProfile[i] = 0;
		}
		
		return radialProfile;
	}
	
	private static double compareWithZLut(float[] radialProfile) {
		
		// A copy to perform operations on. Functionally the same
		float[][] zlutC = new float[zlut.length][zlut[0].length];
				
		//add something for normalization
		for(int i = 0; i<zlut.length; i++) {
			
			// Find difference between absolute difference between radial profile and zlut[i] signal and index j
			for(int j = 0; j < zlut[0].length; j++) {
				//Copy is filled here
				zlutC[i][j] = Math.abs(radialProfile[j]-zlut[i][j]);
			}
			
			//sum that difference and add that to the first column
			float sum = 0f;
			for(int j = 0; j<zlutC[0].length; j++) {
				sum += zlutC[i][j];
			}
			
			zlutC[i][0] = sum;
		}
		
		// Find minimum
		int minDiffIndex = 0;
		for(int i = 0; i < zlutC.length; i++) {
			if (zlutC[i][0] < zlutC[minDiffIndex][0]) {
				minDiffIndex = i;
			}
		}
		
		
		//5 point curve fit
		
		// offset left or right 
		int offset = nPointsQuadFit/2;
		
		//Make sure it stays in bounds
		if(minDiffIndex - offset < 0 || minDiffIndex + offset > zlutC.length-1) {
			//IJ.showMessage("The radial profile of the image was too closely matched to a radial profile on the edge of the ZLUT. This causes the fit to be inacurate as it doesn't have enough data to create a 5 point quadratic fit. Program Quitting. ");
			return Double.NaN;
		}
		
		int leftBound =  minDiffIndex - offset;
		int RightBound = minDiffIndex + offset;
		
		double[] data    = new double[RightBound-leftBound+1]; 
		double[] indexes = {-2.0, -1.0, 0.0, 1.0, 2.0};
		
		
		for(int i = 0; i < RightBound-leftBound+1; i++) {
			data[i] = zlutC[i+leftBound][0];
		}
	
		CurveFitter fit = new CurveFitter(indexes, data);
		fit.doCustomFit("y = a*a*(x - b)*(x - b) + c", new double[] {0,0,0}, false);
		
		double b = fit.getParams()[1]; 
		
		// Fitting isn't correct if it strays from the minimum by more than 2
		if (Math.abs(b) > offset) {
			return Double.NaN;
		}
		
		int delta = zlutHeights[minDiffIndex]-zlutHeights[minDiffIndex-1];
		
		double scaledDifference = b*delta;
		// Readjust b to line up with the minimum difference index
		return scaledDifference + zlutHeights[minDiffIndex];
	}
	
	
	
	//Checks if zlut is in the folder and calls the loading function if found
	private static boolean isZlutInFolder(String directory_path) {
				
		if(!new File(directory_path, "ZLUT.tif").exists()) {
			return false;
		}
		if(!new File(directory_path, "zlutHeights.csv").exists()) {
			return false;
		}
		
		loadZlut(new File(directory_path, "ZLUT.tif"));
		loadZLUTHeights(new File(directory_path, "zlutHeights.csv"));
		
		return true;
	}
	

	// Loads private height variable from integers in file
	private static void loadZLUTHeights(File file) {
		
		// Prealloc
		zlutHeights = new int[zlut.length];
		try {
			//File is program made and follows the csv format
			Scanner scanner = new Scanner(file);
			
			if(scanner.hasNext()) {
				//Split along commas 
				String line = scanner.nextLine();
				String[] split = line.split(",");
				
				// Parse to int
				for(int i = 0; i<zlut.length; i++) {
					zlutHeights[i] = Integer.parseInt(split[i]);
				}
			}
			scanner.close();
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	// Loads private zlut variable with pixels from File
	private static void loadZlut(File zlutFile) {
		
		FloatProcessor ip = (new ImagePlus(zlutFile.getAbsolutePath())).getProcessor().convertToFloatProcessor();
      
        int width = ip.getWidth();
        int height = ip.getHeight();
        
        float[] pixels = (float[]) ip.getPixels();
        
        zlut = new float[width][height];
       
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
            	zlut[x][y] = pixels[y*width + x];
            }
        }
	}


	public static double calculateZCord(double[] xyCordSubPixel, ImageProcessor ip) {
		
		float[] radialProfile = createRadialProfile(xyCordSubPixel, ip);
		double zCord = compareWithZLut(radialProfile);
		
		return zCord;
	}	
}
