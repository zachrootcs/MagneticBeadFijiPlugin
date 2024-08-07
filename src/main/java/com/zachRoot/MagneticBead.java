package com.zachRoot;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;

import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.Roi;

import ij.plugin.AVI_Reader;
import ij.plugin.filter.PlugInFilter;


import ij.process.ImageProcessor;



public class MagneticBead implements PlugInFilter {
	
	//Image attributes
	protected static ImagePlus image;
	private int width;
	private int height;
	
	
	// List of z positions for the image if it has more than 1 frame 
	private static double[]  z_cords;
	
	
	//To do implement
	private String length_unit;
	
	

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
		
		// Square Image is necessary
		if(width != height) {
			IJ.showMessage("Image must be square instead of " + width + "x" + height);
			return;
		}

		z_cords = new double[image.getImageStackSize()];
		
		ZPositioning.createZLut(image);
		processStack(image.getStack());
		display();
	}
	

	
	private void display() {
		double[] indexes = new double[z_cords.length];
	
		for(int i = 0; i<z_cords.length; i++) {
			indexes[i] = i;
		}

		Plot p = new Plot("Z Tracking", "Frame number", "Z Cord");
		p.add("line", indexes, z_cords);
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
		
		//Optional May Remove
		Gui.addPointToOverlay(image, xyCordSubPixel, slice);
		
		double zCord = ZPositioning.calculateZCord(xyCordSubPixel, ip); 
				
		
		//slice is 1-indexed so -1 to convert to 0 indexed 
		z_cords[slice-1] = zCord;
}

	// Returns an unsorted list of Comparable Images that were found in directory
    public static ArrayList<ComparableImagePlus> getReferenceImages(String directory_path) {
		
		AVI_Reader aviRead = new AVI_Reader();
		
		// Parent directory kept because the title should be the height of the image (IF IN A ZLUT)
		File parent_directory = new File(directory_path);
		File[] directory = parent_directory.listFiles();
		
        ArrayList<ComparableImagePlus> images = new ArrayList<>();
        for (File file: directory) {
        	
        	if(file.isDirectory()) {
        		images.addAll(getReferenceImages(file.getAbsolutePath()));
        	}
        	
            
            if(file.getName().endsWith(".avi")){
            	
            	//Dumb way to get all the images 
            	//Forced to becasue of how james organized the data            	
            	ImageProcessor img = aviRead.makeStack(file.getAbsolutePath(),1,0,false,false,false).getProcessor(1);
            	int height = Integer.parseInt(parent_directory.getName());
            	long time = getTimeFromString(file.getName());
            	String title = "Image at height: " + height + " and time: " + time;
            	
            	//Name the image with the proper name
            	images.add(new ComparableImagePlus(title, img, height, time));
            	
            }
                
        }
        
        return images;
        
        
		/*String regex = "^\\d+[A-Za-z]+\\.[A-Za-z0-9]+$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(file.getName());

		 * if (matcher.matches()) {
                images.add(new ImagePlus(file.getAbsolutePath()));
                
            } else
		 * 
		 * 
		 * 
		 * 
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

	
	private static long getTimeFromString(String filename) {
		Pattern pattern = Pattern.compile("\\d+(?=\\.avi$)");
		Matcher matcher = pattern.matcher(filename);

		if (matcher.find()) {
		    long time = Long.parseLong(matcher.group());
		    return time;
		}
		throw new RuntimeException("No time could be extracted from: " +filename);
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
		
		//Two beads against the same zlut (Should match each others movement)
		testTwoBeads();
		
		// Testing fluctuating bead against its actual position
		testfluctuatingbead();
		
	}
	
	private static void testfluctuatingbead() {
		String beadDirectory = "C:\\Users\\7060 Yoder3\\Desktop\\MagneticBeadProject\\07-16-24 Data\\Bead 1 Fluctuating ZLUT 50-55";
		
		ArrayList<ComparableImagePlus> imgs = getReferenceImages(beadDirectory);
		Collections.sort(imgs, ComparableImagePlus.TIME_COMPARATOR);
		
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
		ArrayList<ComparableImagePlus> imgs1 = getReferenceImages(bead1Zlut1Directory);
		Collections.sort(imgs1, ComparableImagePlus.TIME_COMPARATOR);
		
		for(ImagePlus img: imgs1) {
			stack1.addSlice(img.getProcessor());
		}
		
		ImageStack stack2 = new ImageStack();
		ArrayList<ComparableImagePlus> imgs2 = getReferenceImages(bead1Zlut2Directory);
		Collections.sort(imgs2, ComparableImagePlus.TIME_COMPARATOR);
		
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