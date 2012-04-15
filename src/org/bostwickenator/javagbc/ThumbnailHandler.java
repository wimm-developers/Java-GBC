package org.bostwickenator.javagbc;

import java.util.Arrays;
import java.util.HashMap;

import android.graphics.Bitmap;

public class ThumbnailHandler {
	public HashMap<Integer, Bitmap> thumbs = new HashMap<Integer, Bitmap>();

	public static int[] unpack(int stateId, String array) throws Exception {
		String[] vals = array.split(" ");
		int[] result = new int[vals.length];
		for (int i = 0; i < vals.length; i++)
			result[i] = Integer.parseInt(vals[i]);
		return result;
	}

	public static String pack(int[] imageData) {
		String temp = Arrays.toString(imageData).replace(", ", " ");
		return temp.substring(1, temp.length() - 1);
	}
}
