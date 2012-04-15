package org.bostwickenator.javagbc;

import java.io.File;
import java.io.FileInputStream;
import java.util.zip.ZipInputStream;

public class Utils {

	
	 /** Returns the unsigned value (0 - 255) of a signed byte */
	 static public short unsign(byte b) {
	  if (b < 0) {
	   return (short) (256 + b);
	  } else {
	   return b;
	  }
	 }
	 
	 static public short unsign(int b) {
		 return unsign((byte)b);
	 }
	 
	 

	 /** Returns the unsigned value (0 - 255) of a signed 8-bit value stored in a short */
	 static public short unsign(short b) {
	  if (b < 0) {
	   return (short) (256 + b);
	  } else {
	   return b;
	  }
	 }
	 
	 public static void backupOld(String filename,long saveTime){
		 
		 File old3 = new File(filename+".bak3");
		 File old2 = new File(filename+".bak2");
		 File old1 = new File(filename+".bak1");
		 File old0 = new File(filename);
		 
		 if(old3.exists())
			 old3.delete();
		 
		 if(old2.exists()){
			 old2.renameTo( old3 );
		 }
		 
		 
		 if(old1.exists()){
			 old1.renameTo( old2 );
		 }
		 
		 if(old0.exists()){
			 old0.renameTo( old1);
		 }
		 
		 
	 }
	 
	 public static boolean isRomFile(String path){
		 
		 String lower = path.toLowerCase();
		 
		 return lower.endsWith(".gbc") || lower.endsWith(".cgb") || lower.endsWith(".gb");
		 
	 }
	 
	 
	 public static class ZipedRom{
		 public ZipInputStream mZipInputStream;
		 public String name;
	 }
	 
	 public static ZipedRom findRomInZip(String filePath){
		 
		 ZipedRom ret = new ZipedRom();
		 
		 
		 java.util.zip.ZipInputStream zip;

			try {

				zip = new ZipInputStream(new FileInputStream(filePath));

				// Check for valid files (GB or GBC ending in filename)
				java.util.zip.ZipEntry ze;

				boolean bFoundGBROM=false;
				
				while ((ze = zip.getNextEntry()) != null) {
					String str = ze.getName();
					if (Utils.isRomFile(str)) {
						bFoundGBROM = true;
						ret.name = stripExtention(str);
						// Leave loop if a ROM was found.
						break;
					}
				}
				// Show an error if no ROM file was found in the ZIP
				if (!bFoundGBROM) {
					System.err.println("No GBx ROM found!");
					throw new java.io.IOException("ERROR");
				}else{
					ret.mZipInputStream = zip;
				}
				System.out.println("Found " + ret.name);
				return ret;
			} catch (Exception e) {
				System.out.println(e);
				return null;
			}
		 
	 } 
	 
	 
	 public static String stripExtention(String filename) {
			if (filename == null)
				return null;
			int dotPosition = filename.lastIndexOf('.');

			if (dotPosition != -1) {
				return filename.substring(0, dotPosition);
			} else {
				return filename;
			}
		}
}
