package com.zachRoot;

import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.measure.CurveFitter;
import ij.plugin.AVI_Reader;
import ij.plugin.filter.PlugInFilter;

import ij.process.FloatProcessor;
import ij.process.ImageProcessor;



public class MagneticBead implements PlugInFilter {
	private ImagePlus image;
	
	private float[][] zlut;
	private int[]     zlutHeights; 
	
	private int width;
	private int height;
	private int radius;
	
	//To do implement
	private String length_unit;
	
	private int centerRoiWidth = 10;
	private int centerRoiHeight = 10;
	

	@Override
	public int setup(String arg, ImagePlus image) {
		if (arg.equals("about")) {
			showAbout();
			return DONE;
		}
		
		this.image = image;
		return DOES_8G | DOES_16 | DOES_32;
	}

	@Override
	public void run(ImageProcessor ip) {
		
		width = ip.getWidth();
		height = ip.getHeight();
		
		radius = width/3 - width%2;
		
		// Square Image is necessary
		if(width != height) {
			IJ.showMessage("Image must be square instead of " + width + "x" + height);
			return;
		}
		
		// Creates ZLut
		createZLut();
		processStack(image.getStack());
		
	}
	
	private void createZLut() {
		
		
		// Directory for ZLUT Reference Images
		String directory_path = getDirectoryFromUser();		
		
		
		if(isZlutInFolder(directory_path)) {
			System.out.println("ZlutSaved");
			//already saves
			return;
		}
		
		// ZLut Component Images
		LinkedList<ImagePlus> images = getReferenceImages(directory_path);
		
		// Matrix with each index being the radial profile of image about center of bead
		zlut        = new float[images.size()][radius+1];
		zlutHeights = new int  [images.size()];
		
		for(int i = 0; i<images.size(); i++) {
			ImageProcessor ip = images.get(i).getProcessor();
			
			// X,Y COM Center Coordinates
			int[] xyCord = getCenterOfMass(ip);
			
			// Sub Pixel Localization utilizing cross correlation
			double[] xyCordSubPixel = xyRowColConvolve(ip, xyCord[0], xyCord[1]);
			
			//Optional May Remove
			//addPointToOverlay(xyCordSubPixel, 0);
			
			float[] radialProfile = createRadialProfile(xyCordSubPixel, ip);
			zlut[i] = radialProfile;
			
			// Get the height of the image from the image title
			zlutHeights[i] = extractStartingNumber(images.get(i).getTitle());

		}
		
		promptUserToSaveZLut(directory_path); 
	}
	
	
	public void processStack(ImageStack stack) {
		//Start at one and process each frame in stack
		for(int i = 1; i<=stack.size(); i++ ) {
			processIP(stack.getProcessor(i), i);
		}
	}
	
	
	// directory_path is the default go to
	private void promptUserToSaveZLut(String directory_path) {
		
		GenericDialog g = new GenericDialog("Save ZLUT?");
		g.addMessage("Select a folder to save the ZLUT to");
		g.addDirectoryField("Directory", directory_path);
		g.showDialog();
		
		if(g.wasCanceled()) {
			return;
		}
		
		directory_path = g.getNextString();
		
		//Save ZLUT
		FileSaver f = new FileSaver(new ImagePlus("ZLUT", new FloatProcessor(zlut)));
		f.saveAsTiff(directory_path + "ZLUT.tif");
		
		//Save Heights
		String zlutHeightsPath = directory_path + "zlutHeights.csv";
		File zlutHeightsfile = new File(zlutHeightsPath); 
		
		try {
			FileWriter outputfile = new FileWriter(zlutHeightsfile); 
			String output = "";
			
			for(int i: zlutHeights) {
				output += i + ",";
			}
			
			outputfile.write(output);
			outputfile.close();
		}
		catch(IOException e){
			IJ.showMessage("ZLUT could not be saved");
		}
		
		return;
	}

	
	public int[] getCenterOfMass(ImageProcessor ip) {
		int[] xyCord = null;
		//TO DO CHECK IF THIS MAKES SENSE?
		int type = image.getType();
		
		if (type == ImagePlus.GRAY32)
			xyCord = fitBeadByCenterOfMass((float[])ip.getPixels());
		else if (type == ImagePlus.GRAY8 || type == ImagePlus.GRAY16)
			xyCord = fitBeadByCenterOfMass((float[])ip.convertToFloatProcessor().getPixels());
		else {
			throw new RuntimeException("Image Type Not Supported");
		}
		
		return xyCord;
	}
	

	
	public void processIP(ImageProcessor ip, int slice) {
		
		// X,Y COM Center Coordinates
		int[] xyCord = getCenterOfMass(ip);
		
		// Sub Pixel Localization utilizing cross correlation
		double[] xyCordSubPixel = xyRowColConvolve(ip, xyCord[0], xyCord[1]);
		
		//Optional May Remove
		addPointToOverlay(xyCordSubPixel, slice);
		
		float[] radialProfile = createRadialProfile(xyCordSubPixel, ip);
		
		double zCord = compareWithZLut(radialProfile);
		
		System.out.println("The Calculated Z Cord is " + zCord);
	}
	
	
	private double compareWithZLut(float[] radialProfile) {
		
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
		//Make sure it stays in bounds
		/*
		if(minDiffIndex-2 < 0 || minDiffIndex+2 > zlutC[0].length-1) {
			IJ.showMessage("The radial profile of the image was too closely matched to a radial profile on the edge of the ZLUT. This causes the fit to be inacurate as it doesn't have enough data to create a 5 point quadratic fit. Program Quitting. ");
			return Double.NaN;
		}
		*/
		int leftBound = (minDiffIndex-2 < 0) ? 0 : minDiffIndex-2;
		int RightBound = (minDiffIndex+2 > zlutC.length-1) ? zlutC.length-1 : minDiffIndex+2;
		
		double[] data    = new double[RightBound-leftBound+1];
		double[] indexes = {-2.0, -1.0, 0.0, 1.0, 2.0};
		
		for(int i = 0; i < RightBound-leftBound+1; i++) {
			data[i] = zlutC[i+leftBound][0];
		}
	
		CurveFitter leastdiferenceFit = new CurveFitter(indexes, data);
		leastdiferenceFit.doCustomFit("y = a + b*x + c*c*x*x", new double[] {0,0,0}, false);
		
		// -b/2c^2 because c^2 is the variable fit in the equation and + mindiff to readjust
		return -leastdiferenceFit.getParams()[1]/(2*leastdiferenceFit.getParams()[2]*leastdiferenceFit.getParams()[2])+zlutHeights[minDiffIndex];
	}

	
	
	
	
	private float[] createRadialProfile(double[] xyCord, ImageProcessor ip) {
		
		//ip.getOverlay().add(new OvalRoi(xyCord[0], xyCord[1], radius, radius));
		
		assert(radius%2==0);
		
		int[] rho = new int[width*width];
		int[] numRho = new int[radius+1];
		float[] intensitySum = new float[radius+1];
		float[] pixels = (float[])ip.convertToFloatProcessor().getPixels(); 
		
		for(int x = 0; x < width; x++) {
			for(int y = 0; y < width; y++) {
				int rhoValue = (int)(Math.round(Math.sqrt((xyCord[0]-x)*(xyCord[0]-x) + (xyCord[1]-y)*(xyCord[1]-y))));
				
				if(rhoValue > radius) rhoValue = radius;
				//if(rhoValue < 1)      rhoValue = 1;
				
				//Can remove is used for testing
				rho[x+y*width] = rhoValue;
				
				
				intensitySum[rhoValue] += pixels[x+y*width];
				
				numRho[rhoValue] +=1;
			}
		}
		//System.out.println(Arrays.toString(numRho));
		//System.out.println(Arrays.toString(intensitySum));
		//FloatProcessor fp = new FloatProcessor(width, width, rho);
		//new ImagePlus("",fp).show();
		
		
		float[] radialProfile = new float[radius+1];
		for(int i = 0; i < radius+1; i++) {
			radialProfile[i] = intensitySum[i]/numRho[i];
			if (Double.isNaN(radialProfile[i])) radialProfile[i] = 0;
		}
		
		
		//new ImagePlus("",new FloatProcessor(radius+1,1,radialProfile)).show();
		return radialProfile;
	}
	
	
	// Perfectly fine that the return value doesn't have float precision
	private int[] subtractMean(int[] data) {
		
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
	
	// Sub Pixel Localization 
	private double[] xyRowColConvolve(ImageProcessor ip, int xPos, int yPos) {
		
		// Create local width to not impact other threads
		int width = this.width;
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
		if(image.getWidth() == width+1) {width = width + 1;}
		
		// Average position with center to adjust for Convolution algorithm
		xCord = ((width)/2f-xCord)/2 + xCord;
 		yCord = ((width)/2f-yCord)/2 + yCord;
		
		
		return new double[]{xCord,yCord};
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
	
	
	public int[] fitBeadByCenterOfMass(float[] flattenedImage) {
    	
        float[][] T = new float[width][height];
        
       
        // Calculate absolute differences
        float mean = calculateMean(flattenedImage);
        
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
            	T[i][j] = Math.abs(flattenedImage[i * width + j] - mean);
            }
        }
        //ImagePlus img = new ImagePlus("Temp" + mean, new FloatProcessor(T));
        //img.show();
        
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
	
    
    
	public void addPointToOverlay(double[] xyCord, int sliceNumber) {
		// Create new overlay if one doesn't exist
		if(image.getOverlay() == null) image.setOverlay(new Overlay());
		
		// New Circle around the center of the point
		Roi centerPoint = new OvalRoi(xyCord[0]-centerRoiWidth/2, xyCord[1]-centerRoiHeight/2,centerRoiWidth,centerRoiHeight);
		
		// Set time position
		centerPoint.setPosition(sliceNumber);
		
		centerPoint.setStrokeColor(Color.GREEN);
		
		image.getOverlay().add(centerPoint);
		
	}

	
	// function to return 
	private String getDirectoryFromUser() {
		
		GenericDialog g = new GenericDialog(
				"Select Directory for Magnetic Bead Z Space Localization"
		);
		
		g.addMessage(
				"Each image in the directory must be titled with the number "
				+ "corresponding with the Z position and unit (i.e. 200nm.tif) "
				+ "\nor the ZLut must already be saved there");
		
		g.addDirectoryField("Directory", "C:\\Users\\");
		g.showDialog();
		
		
		return g.getNextString();
		
	}
	
	private LinkedList<ImagePlus> getReferenceImages(String directory_path) {
		
		AVI_Reader aviRead = new AVI_Reader();
		
		File parent_directory = new File(directory_path);
		File[] directory = parent_directory.listFiles();
		
		String regex = "^\\d+[A-Za-z]+\\.[A-Za-z0-9]+$";
        Pattern pattern = Pattern.compile(regex);
        
        LinkedList<ImagePlus> images = new LinkedList<>();
        for (File file: directory) {
        	
        	if(file.isDirectory()) {
        		images.addAll(getReferenceImages(file.getAbsolutePath()));
        	}
        	
            Matcher matcher = pattern.matcher(file.getName());
            
            if (matcher.matches()) {
                images.add(new ImagePlus(file.getAbsolutePath()));
                
            } else if(file.getName().endsWith(".avi")){
            	
            	//Dumb way to get all the images 
            	//Forced to becasue of how james organized the data
            	//Convert to imageProcessor 
            	ImageProcessor img = aviRead.makeStack(file.getAbsolutePath(),1,0,false,false,false).getProcessor(1);
            	//Name the image with the proper name
            	images.add(new ImagePlus(parent_directory.getName(),img));
            	
            }
                
        }
        
        return images;
        
        
		/*
		 * 	;
		 * file.getName().endsWith(".avi")
		for(File subDir: directory) {
			if(subDir.isDirectory()) {
				for(File file: subDir.listFiles()) {
					if(file.getName().endsWith(".avi"))
				}
			}
		}
		// of the form: numbers letters . extension
		File[] directory = new File(directory_path).listFiles();
		
		String regex = "^\\d+[A-Za-z]+\\.[A-Za-z0-9]+$";
        Pattern pattern = Pattern.compile(regex);
        
        LinkedList<ImagePlus> images = new LinkedList<>();
        for (File file: directory) {
        	
            Matcher matcher = pattern.matcher(file.getName());
            
            if (matcher.matches() ) {
                System.out.println(file.getName() + " matches the pattern.");
                images.add(new ImagePlus(file.getAbsolutePath()));
                
            } else {
                System.out.println(file.getName() + " does not match the pattern.");
            }
        }
        return images;
        
        */
	}

	
	//Checks if zlut is in the folder and calls the loading function if found
	private boolean isZlutInFolder(String directory_path) {
				
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
	private void loadZLUTHeights(File file) {
		
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
	private void loadZlut(File zlutFile) {
		
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

	// Title of image should be numbers units . extension
	// Returns starting numbers
	private int extractStartingNumber(String title) {
		
		//regex for starting numbers
	 	Pattern pattern = Pattern.compile("^\\d+");
        Matcher matcher = pattern.matcher(title);
        
        if (matcher.find()) {
            String numbers = matcher.group(); 
            return Integer.parseInt(numbers);
        } else {
        	throw new RuntimeException("Title does not contain starting numbers");
        }
 
	}	


	
	public void showAbout() {
		IJ.showMessage("Magnetic Bead Analysis", "Java Port of Matlab Project");
	}
	
	/**
	 * Main method for debugging.
	 *
	 * For debugging, it is convenient to have a method that starts ImageJ, loads
	 * an image and calls the plugin, e.g. after setting breakpoints.
	 *
	 * @param args unused
	 */
	public static void main(String[] args) throws Exception {
		// set the plugins.dir property to make the plugin appear in the Plugins menu
		// see: https://stackoverflow.com/a/7060464/1207769
		Class<?> clazz = MagneticBead.class;
		java.net.URL url = clazz.getProtectionDomain().getCodeSource().getLocation();
		java.io.File file = new java.io.File(url.toURI());
		System.setProperty("plugins.dir", file.getAbsolutePath());
		
		// start ImageJ
		new ImageJ();
//C:\\Users\\7060 Yoder3\\Desktop\\MagneticBeadProject\\videos\\croped3.tif
		ImagePlus image = new ImagePlus("C:\\Users\\7060 Yoder3\\Desktop\\MagneticBeadProject\\07-16-24 Data\\Bead 1 ZLUT 1 50-55\\54870\\2024-07-16-48862837.avi");
		image.show();
		
		// run the plugin
		IJ.runPlugIn(clazz.getName(), "");
		
	}
}