package com.zachRoot;

import java.util.Comparator;

import ij.ImagePlus;
import ij.process.ImageProcessor;


// An extension of ImagePlus that allows time and the z position to be compared
public class ComparableImagePlus extends ImagePlus{
	
	
	private double zPos;
	private long time;
	
	//If two images come from the same video they'll have the same time
	//the frame variable allows them to be sorted 
	private int frame;
	
	public static final Comparator<ComparableImagePlus> TIME_COMPARATOR = new TimeComparator();
	public static final Comparator<ComparableImagePlus> Z_COMPARATOR = new ZComparator();

	public ComparableImagePlus(String title, ImageProcessor ip, double height, long time2, int frame) {
		super(title, ip);
		this.zPos = height;
		this.time = time2;
		this.frame = frame;
	}

	public double getZPos() {
		return zPos;
	}
	
	public long getTime() {
		return time;
	}
	
	
	static class TimeComparator implements Comparator<ComparableImagePlus>{

		@Override
		public int compare(ComparableImagePlus o1, ComparableImagePlus o2) {
			
			if(o1.time != o2.time) {
				return Long.compare(o1.time, o2.time);
			}else {
				//Sort by frame number instead
				return o1.frame - o2.frame;
			}
			
		}
	}
	static class ZComparator implements Comparator<ComparableImagePlus>{

		@Override
		public int compare(ComparableImagePlus o1, ComparableImagePlus o2) {
			// A way of preventing casting to int and losing decimal data 
			return Double.compare(o1.zPos, o2.zPos);
		}
		
	}
}
