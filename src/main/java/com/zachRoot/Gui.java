package com.zachRoot;

import java.awt.Color;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.process.FloatProcessor;

public class Gui {
	
	// Center Roi values for UI
	private static int centerRoiWidth = 10;
	private static int centerRoiHeight = 10;
	
	
	// directory_path is the default go to
	public static void promptUserToSaveZLut(String directory_path) {
		
		GenericDialog g = new GenericDialog("Save ZLUT?");
		g.addMessage("Select a folder to save the ZLUT to");
		g.addDirectoryField("Directory", directory_path);
		g.showDialog();
		
		if(g.wasCanceled()) {
			return;
		}
		
		directory_path = g.getNextString();
		
		//Save ZLUT
		FileSaver f = new FileSaver(new ImagePlus("ZLUT", new FloatProcessor(ZPositioning.zlut)));
		f.saveAsTiff(directory_path + "ZLUT.tif");
		
		//Save Heights
		String zlutHeightsPath = directory_path + "zlutHeights.csv";
		File zlutHeightsfile = new File(zlutHeightsPath); 
		
		try {
			FileWriter outputfile = new FileWriter(zlutHeightsfile); 
			String output = "";
			
			for(int i: ZPositioning.zlutHeights) {
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
	
	public static String getDirectoryFromUser(String title, String message) {
		
		GenericDialog g = new GenericDialog(title);
		
		g.addMessage(message);
		
		g.addDirectoryField("Directory", "C:\\Users\\");
		g.showDialog();
		
		
		return g.getNextString();
		
	}
	
    
	public static void addPointToOverlay(ImagePlus image, double[] xyCord, int sliceNumber) {
		// Create new overlay if one doesn't exist
		if(image.getOverlay() == null) image.setOverlay(new Overlay());
		
		// New Circle around the center of the point
		Roi centerPoint = new OvalRoi(xyCord[0]-centerRoiWidth/2, xyCord[1]-centerRoiHeight/2,centerRoiWidth,centerRoiHeight);
		
		// Set time position
		centerPoint.setPosition(sliceNumber);
		
		centerPoint.setStrokeColor(Color.GREEN);
		
		image.getOverlay().add(centerPoint);
		
	}
}
