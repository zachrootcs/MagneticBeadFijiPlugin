package com.zachRoot;

import java.io.File;
import java.util.ArrayList;
import java.util.IllegalFormatException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.Plot;
import ij.plugin.AVI_Reader;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;



public class MagneticBead implements PlugIn {
	
	//Image attributes
	protected static ImagePlus image;
	private int width;
	private int height;
	
	
	// List of positions for the image 
	private static double[]  z_cords;
	private static double[]  x_cords;
	private static double[]  y_cords;
	
	//To do implement
	//private String length_unit;
	
	
	@Override
	public void run(String arg) {
		
		if (arg.equals("about")) {
			showAbout();
			return;
		}
		
		image = WindowManager.getCurrentImage();
		
		// no image found
		if(image == null) {
			image = retrieveUserImage();
			
			// no images found in user directory
			if(image == null) {
				IJ.showMessage("Could not find any images in that directory");
				return;
			}
		}
		
		image.show();
		
		initializeVariables();
		validateImage();
		
		ZPositioning.createZlut(image);
		processStack(image.getStack());
		display();
		
	}
	


	// Displays the x vs y position and z position vs frame number on a plot
	private void display() {
		
		double[] indexes = new double[z_cords.length];
	
		for(int i = 0; i<z_cords.length; i++) {
			indexes[i] = i;
		}

		Plot p = new Plot("Z Tracking", "Frame number", "Z Coordinate");
		p.add("line", indexes, z_cords);
		p.show();
		
		p = new Plot("XY Tracking", "X Coordinate", "Y Coordinate");
		p.add("line", x_cords, y_cords);
		p.show();
		
	}

	public void processStack(ImageStack stack) {
		//Start at one and process each frame in stack
		for(int i = 1; i<=stack.size(); i++ ) {
			processIP(stack.getProcessor(i), i);
		}
	}
	

	public void processIP(ImageProcessor ip, int slice) {
		
		double[] xyCordSubPixel = XYPositioning.getBeadCenter(image, ip);
		
		Gui.addPointToOverlay(image, xyCordSubPixel, slice);
		
		double zCord = ZPositioning.calculateZCord(xyCordSubPixel, ip); 
				
		//slice is 1-indexed so -1 to convert to 0 indexed 
		z_cords[slice-1] = zCord;
		x_cords[slice-1] = xyCordSubPixel[0];
		y_cords[slice-1] = xyCordSubPixel[1];
}

	// Returns an unsorted list of Comparable Images that were found in directory
	// This list will also need to be filtered to remove the
    public static ArrayList<ComparableImagePlus> getReferenceImages(String directory_path, boolean isZLUT) {
		
		AVI_Reader aviRead = new AVI_Reader();
		
		// Parent directory kept because the title should be the height of the image (IF IN A ZLUT)
		File parent_directory = new File(directory_path);
		File[] directory = parent_directory.listFiles();
		
        ArrayList<ComparableImagePlus> images = new ArrayList<>();
        
        for (File file: directory) {
        	
        	if(file.isDirectory()) {
        		images.addAll(getReferenceImages(file.getAbsolutePath(), isZLUT));
        	}
        	
        	//Add support for more files
        	if(!file.getName().endsWith(".avi")){continue;}
        	
        	ImageStack imgstk = aviRead.makeStack(file.getAbsolutePath(),1,0,false,false,false);
        	            	
        	// for each frame in video stack
        	for(int i = 1; i<=imgstk.size(); i++) {
        		
        		ImageProcessor img = imgstk.getProcessor(i);
        		double height = Double.NaN;
        		long time = Long.MIN_VALUE;
        		
        		// ZLut only requires height while other should require time
        		if(isZLUT) {
        			try {
            			height = Double.parseDouble(parent_directory.getName());
            		} catch (NumberFormatException e) {
            			// Cannot have an image without a height in the zlut
            			continue;
            		}
        		} else {
        			try {	
                    	time = getTimeFromString(file.getName());
            		} catch(IllegalFormatException e) {
            			// Cannot have an image without a time in an image that will get processed
            			continue;
            		}
        		}
        		
    		   	String title = "Image at height: " + height + " and time: " + time;
            	images.add(new ComparableImagePlus(title, img, height, time, i));      	
        	}
        }
        
        if(isZLUT) {
    		images.sort(ComparableImagePlus.Z_COMPARATOR);
    	} else {
    		images.sort(ComparableImagePlus.TIME_COMPARATOR);
    	}
        
        return images;
        
	}


	private void validateImage() {
		// Square Image is necessary
		if(width != height) {
			IJ.showMessage("Image must be square instead of " + width + "x" + height);
			return;
		}
		//Add Something w bit depth or color if needed

	}


	private void initializeVariables() {
		width = image.getWidth();
		height = image.getHeight();
		z_cords = new double[image.getImageStackSize()];
		x_cords = new double[image.getImageStackSize()];
		y_cords = new double[image.getImageStackSize()];
	}
	
	
	private static long getTimeFromString(String filename) {
		Pattern pattern = Pattern.compile("\\d+(?=\\.avi$)");
		Matcher matcher = pattern.matcher(filename);

		if (matcher.find()) {
		    long time = Long.parseLong(matcher.group());
		    return time;
		}
		throw new IllegalArgumentException("No time could be extracted from: " +filename);
	}
	
	// Retrieves an image using getReferenceImages() sorted by time and converted 
	private ImagePlus retrieveUserImage() {
		String user_directory = Gui.getDirectoryFromUser("Select Folder for Image","Select a directory. Each frame or video should be titled with the time it was taken in year-month-day-millisecond format.");
		
		ArrayList<ComparableImagePlus> images = getReferenceImages(user_directory,false);
		
		// No images found
		if (images.size()==0) return null;
		
		ImageStack imgstk = new ImageStack();
		for(ComparableImagePlus img: images) {
			imgstk.addSlice(img.getProcessor());
		}
		
		return new ImagePlus("Image from: " + user_directory, imgstk);
	}

	
	public void showAbout() {
		IJ.showMessage("Magnetic Bead Analysis", "Java Port of Matlab Project");
	}
	
	
	public static void main(String[] args) throws Exception {
		// set the plugins.dir property to make the plugin appear in the Plugins menu
		// see: https://stackoverflow.com/a/7060464/1207769
		Class<?> clazz = MagneticBead.class;
		java.net.URL url = clazz.getProtectionDomain().getCodeSource().getLocation();
		java.io.File file = new java.io.File(url.toURI());
		System.setProperty("plugins.dir", file.getAbsolutePath());
		
		
		//test no image
		testNoImage();
		//Two beads against the same zlut (Should match each others movement)
		//testTwoBeads();
		
		// Testing fluctuating bead against its actual position
		//testfluctuatingbead();
		
	}
	
	private static void testNoImage() {
		new ImageJ();
		IJ.runPlugIn(MagneticBead.class.getName(), "");
	}

	private static void testfluctuatingbead() {
		String beadDirectory = "C:\\Users\\7060 Yoder3\\Desktop\\MagneticBeadProject\\07-16-24 Data\\Bead 1 Fluctuating ZLUT 50-55";
		
		ArrayList<ComparableImagePlus> imgs = getReferenceImages(beadDirectory, false);
		
		ImageStack stack = new ImageStack();
		for(ImagePlus img: imgs) {
			stack.addSlice(img.getProcessor());
		}
		
		
		//run it back w the other directory
		image = new ImagePlus("Fluctuating bead", stack);
		image.show();
		
		IJ.runPlugIn(MagneticBead.class.getName(), "");
		double[] fluctuating_bead_z_cords = z_cords;
		double[] actual_z_cords = new double[z_cords.length];
		double[] difference = new double[z_cords.length];
		double[] indexes = new double[z_cords.length];
		
		for(int i = 0; i<z_cords.length; i++) {
			indexes[i] = i;
			actual_z_cords[i] = imgs.get(i).getZPos();
			difference[i] = fluctuating_bead_z_cords[i]-actual_z_cords[i];
		}


		Plot p = new Plot("Z Tracking", "Frame number", "Z Cord");
		p.add("line", indexes, fluctuating_bead_z_cords);
		p.add("line", indexes, actual_z_cords);
		p.show();
		
		Plot d = new Plot("Z Tracking Difference", "Frame number", "Z Cord Difference");
		d.add("line", indexes, difference);
		d.show();
		
	}

	private static void testTwoBeads() {
		//Directories
		String bead1Zlut1Directory = "C:\\Users\\7060 Yoder3\\Desktop\\MagneticBeadProject\\07-16-24 Data\\Bead 1 ZLUT 1 50-55";
		String bead1Zlut2Directory = "C:\\Users\\7060 Yoder3\\Desktop\\MagneticBeadProject\\07-16-24 Data\\Bead 1 ZLUT 2 50-55";
		
		//Convert individual images into stack
		ImageStack stack1 = new ImageStack();
		ArrayList<ComparableImagePlus> imgs1 = getReferenceImages(bead1Zlut1Directory, false);
		
		for(ImagePlus img: imgs1) {
			stack1.addSlice(img.getProcessor());
		}
		
		ImageStack stack2 = new ImageStack();
		ArrayList<ComparableImagePlus> imgs2 = getReferenceImages(bead1Zlut2Directory, false);
		
		for(ImagePlus img: imgs2) {
			stack2.addSlice(img.getProcessor());
		}
		
		// start ImageJ
		new ImageJ();
		//ImagePlus image =  new ImagePlus("C:\\Users\\7060 Yoder3\\Desktop\\MagneticBeadProject\\07-16-24 Data\\Bead 1 ZLUT 2 50-55\\54630\\2024-07-16-49425064.avi");
		//image.show();
		//IJ.runPlugIn(clazz.getName(), "");
		
		
		ImagePlus image = new ImagePlus("Bead1Zlut1", stack1);
		image.show();
	
		// run the plugin
		IJ.runPlugIn(MagneticBead.class.getName(), "");
		double[] bead1Zlut1_z_cords = z_cords;
		image.close();
		
		
		//run it back w the other directory
		image = new ImagePlus("Bead1Zlut2", stack2);
		image.show();
		IJ.runPlugIn(MagneticBead.class.getName(), "");
		double[] bead1Zlut2_z_cords = z_cords;
		
		double[] indexes = new double[z_cords.length];
		for(int i = 0; i<z_cords.length; i++) {
			indexes[i] = i;
		}


		Plot p = new Plot("Z Tracking Difference", "Frame number", "Z Cord");
		p.add("line", indexes, bead1Zlut1_z_cords);
		p.add("line", indexes, bead1Zlut2_z_cords);
		p.show();
		
	}


	
}