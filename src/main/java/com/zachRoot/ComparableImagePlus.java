package com.zachRoot;

import java.util.Comparator;

import ij.ImagePlus;
import ij.process.ImageProcessor;


// An extension of ImagePlus that allows time and the z position to be compared
public class ComparableImagePlus extends ImagePlus{
	
	private int zPos;
	private long time;
	
	public static final Comparator<ComparableImagePlus> TIME_COMPARATOR = new TimeComparator();
	public static final Comparator<ComparableImagePlus> Z_COMPARATOR = new ZComparator();

	public ComparableImagePlus(String title, ImageProcessor ip, int height, long time2) {
		super(title, ip);
		this.zPos = height;
		this.time = time2;
	}

	public int getZPos() {
		return zPos;
	}
	
	public long getTime() {
		return time;
	}
	
	
	static class TimeComparator implements Comparator<ComparableImagePlus>{

		@Override
		public int compare(ComparableImagePlus o1, ComparableImagePlus o2) {
			// TODO Auto-generated method stub
			return (int) (o1.time - o2.time);
		}
		
	}
	static class ZComparator implements Comparator<ComparableImagePlus>{

		@Override
		public int compare(ComparableImagePlus o1, ComparableImagePlus o2) {
			// TODO Auto-generated method stub
			return o1.zPos - o2.zPos;
		}
		
	}
}
