package org.bostwickenator.javagbc;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.util.Date;

import org.bostwickenator.javagbc.Utils.ZipedRom;

import android.graphics.Bitmap;
import android.view.KeyEvent;
import android.widget.Toast;

public class RomController {


	static int[] key = new int[] { KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_A,
			KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_M,
			KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_ENTER };

	static String romFile = null;
	static int romId = 0;
	static String stateChanged = "";

	//static RomRenderer render;
	static RomSurfaceView render;
	static Dmgcpu dmgcpu;
	public static boolean draw = true;

	static boolean isPaused;

	/** Compressed file types */
	final static byte bNotCompressed = 0;
	final static byte bZip = 1;
	final static byte bJar = 2;
	final static byte bGZip = 3;

	public static boolean loadRom(String state) {
		if (romFile == null)
			return false;
		byte[] data = restoreState(state);
		
		InputStream rom = openRom(romFile);
		if(rom==null){
			return false;
		}
		
		String romName = Utils.stripExtention(romFile);
		
		if (data == null)
			dmgcpu = new Dmgcpu(romName, rom);
		else
			dmgcpu = new Dmgcpu(romName, rom, data);
		
		loadCartRam();
		return true;
	}

	private static InputStream openRom(String romFileName) {
		byte bFormat;
		//boolean bFoundGBROM = false;
		String romName = "None";

		if (romFileName.toUpperCase().indexOf("ZIP") > -1) {
			bFormat = bZip;
		} else if (romFileName.toUpperCase().indexOf("JAR") > -1) {
			bFormat = bZip;
		} else if (romFileName.toUpperCase().indexOf("GZ") > -1) {
			bFormat = bGZip;
		} else {
			bFormat = bNotCompressed;
		}

		// Simplest case, open plain gb or gbc file.
		if (bFormat == bNotCompressed) {
			try {
				romName = Utils.stripExtention(romFileName);
				return new FileInputStream(new File(romFileName));
			} catch (Exception e) {
				System.out.println("Cant open file");
				return null;
			}
		}

		// Should the ROM be loaded from a ZIP compressed file?
		if (bFormat == bZip) {
			System.out.println("Loading ZIP Compressed ROM");

			
			ZipedRom rom = Utils.findRomInZip(romFileName);
			
			
			romName = rom.name;
			return rom.mZipInputStream;
			
			
		}

		if (bFormat == bGZip) {
			System.out.println("Loading GZIP Compressed ROM");
			romName = Utils.stripExtention(romFileName);
			try {
				return new java.util.zip.GZIPInputStream(
						new java.io.FileInputStream(romFileName));
			} catch (Exception e) {
				System.out.println("Can't open file");
				return null;
			}
		}
		return null;
	}

	

	private static byte[] restoreState(String state) {
		try {
			if (state == null || state.equals("")) {
				System.out.println("No saved state");
				return null;
			}

		
			String saveRamFileName = Utils.stripExtention(romFile) +"_"+ state + ".stt";
			
			BufferedInputStream rs = new BufferedInputStream(
					new FileInputStream(saveRamFileName));

			byte[] data = new byte[rs.available()];
			int result = rs.read(data);
			
			rs.close();
			
			if (result > 0) {
				System.out.println("State restored: " + saveRamFileName + " "
						+ saveRamFileName);
				return data;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void runRom(RomSurfaceView render){//RomRenderer render) {

		Dmgcpu.sound =  KiddGBC.prefs.getBoolean("sound", true);
		
		RomController.render = render;
		System.out.println("Rom Started");
		
		dmgcpu.run();
	}
	
	
	
	public static void stopDmgCPU(){
		//dmgcpu.releaseReferences();
		
	}

	public static void saveState(String state) {
		try {
			if (dmgcpu == null || state == null)
				return;
			
			System.out.println("Saving CPU state");
			
			String saveRamFileName = Utils.stripExtention(romFile) + "_" + state
					+ ".stt";
			
			
			byte[] flat = dmgcpu.flatten();
			
			
			Utils.backupOld(saveRamFileName,saveTime);
			

			BufferedOutputStream rs = new BufferedOutputStream(
					new FileOutputStream(saveRamFileName),100*1024);

			rs.write(flat);
			rs.close();

			Bitmap original = Bitmap.createBitmap(
					GraphicsChip.frameBufferImage, 0, 0, 160, 144);
			Bitmap stateImage = Bitmap.createScaledBitmap(original, 160 / 2,
					144 / 2, true);
			int data[] = new int[(160 / 2) * (144 / 2)];
			stateImage.getPixels(data, 0, 160 / 2, 0, 0, 160 / 2, 144 / 2);
			int id = KiddGBC.database.updateStateImage(romId, state, data);
			KiddGBC.thumbs.remove(id);
			System.out.println("State saved: " + romFile + " "
					+ saveRamFileName);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	
	public static boolean keyDown(boolean touch, int keyCode) {
		if (touch) {
			switch (keyCode) {
			case KeyEvent.KEYCODE_DPAD_DOWN:
				keyCode = key[3];
				dmgcpu.buttonUp(0);
				dmgcpu.buttonUp(1);
				dmgcpu.buttonUp(2);
				break;
			case KeyEvent.KEYCODE_DPAD_UP:
				keyCode = key[2];
				dmgcpu.buttonUp(0);
				dmgcpu.buttonUp(1);
				dmgcpu.buttonUp(3);
				break;
			case KeyEvent.KEYCODE_DPAD_LEFT:
				keyCode = key[1];
				dmgcpu.buttonUp(0);
				dmgcpu.buttonUp(2);
				dmgcpu.buttonUp(3);
				break;
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				keyCode = key[0];
				dmgcpu.buttonUp(1);
				dmgcpu.buttonUp(2);
				dmgcpu.buttonUp(3);
				break;
			case KeyEvent.KEYCODE_DPAD_CENTER:
				dmgcpu.buttonUp(0);
				dmgcpu.buttonUp(1);
				dmgcpu.buttonUp(2);
				dmgcpu.buttonUp(3);
				break;
			}
		}
		if (dmgcpu != null) {
			for (int i = 0; i < 8; i++) {
				if (keyCode == key[i]) {
					dmgcpu.buttonDown(i);
				}
			}
		}
		return false;
	}

	public static boolean keyUp(int keyCode) {
		if (dmgcpu != null) {
			for (int i = 0; i < 8; i++) {
				if (keyCode == key[i]) {
					dmgcpu.buttonUp(i);
				}
			}
		}
		return false;
	}


	static long saveTime=0;
	
	public static final void saveCartRam() {
		try {
			if (romFile == null)
				return;
			
			System.out.println("Saving RAM");
			String saveRamFileName = Utils.stripExtention(romFile) + ".sav";
			
			Utils.backupOld(saveRamFileName,saveTime);
			
			BufferedOutputStream rs = new BufferedOutputStream(
					new FileOutputStream(saveRamFileName),100*1024);

			byte[][] ram = dmgcpu.cartRam;

			int bankCount = ram.length;
			int bankSize = ram[0].length;
			int size = bankCount * bankSize + 13;

			byte[] b = new byte[size];

			for (int i = 0; i < bankCount; i++)
				System.arraycopy(ram[i], 0, b, i * bankSize, bankSize);

			System.arraycopy(dmgcpu.rtcReg, 0, b, bankCount * bankSize, 5);
			
			long now = System.currentTimeMillis();
			
			ByteBuffer buf = ByteBuffer.wrap(b,bankCount * bankSize + 5,8);
			//buf.order(ByteOrder.nativeOrder());
			
			LongBuffer longBuf = buf.asLongBuffer();
			
			longBuf.put(now);
			
			//Dmgcpu.setInt(b, bankCount * bankSize + 5, (int) (now >> 32));
			//Dmgcpu.setInt(b, bankCount * bankSize + 9, (int) now);

			for (byte byt : b) {
				rs.write(byt);
			}
			// rs.write(b, 0, b.length);
			rs.close();
			System.out.println("RAM Saved");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static final void loadCartRam() {
		if (romFile == null)
			return;
		try {
			
			String saveRamFileName = Utils.stripExtention(romFile) + ".sav";
			
			BufferedInputStream rs = new BufferedInputStream(
					new FileInputStream(saveRamFileName));
			// RecordStore rs = RecordStore.openRecordStore("20R_" + cartID,
			// true);

			if (rs.available() > 0) {
				byte[][] ram = dmgcpu.cartRam;
				int bankCount = ram.length;
				int bankSize = ram[0].length;

				int total = 0;
				int pos = 0;
				int ramPos = 0;
				while (rs.available() > 0) {
					
					if (pos >= ram[0].length) {
						pos = 0;
						ramPos++;
					}
					if (ramPos >= ram.length)
						break;
					
					byte b = (byte) rs.read();
					// System.out.println(ramPos + " " + pos);
					ram[ramPos][pos] = b;
					total++;
					pos++;

				}
				// byte[] b = new byte[rs.available()];
				// rs.read(b);// .read();//.getRecord(1);

				// for (int i = 0; i < bankCount; i++)
				// System.arraycopy(b, i * bankSize, ram[i], 0, bankSize);

				if (rs.available() > 0){// total == bankCount * bankSize + 13) {
					total = 0;
					pos = 0;
					int pos2=0;
					
					
					
					byte[] bytes = new byte[8];
					
					while (rs.available() > 0 ) {
						byte b = (byte) rs.read();
						if(total < 5){
							dmgcpu.rtcReg[pos] = b;
						}else{
							bytes[pos2++] = b;
						}
						
						pos++;
						total++;
					}
					
					
					ByteBuffer buf = ByteBuffer.wrap(bytes);
					//buf.order(ByteOrder.nativeOrder());
					
					LongBuffer longBuf = buf.asLongBuffer();
					
					long time = longBuf.get(0);
					
					/*
					
					Long time = System.currentTimeMillis();
					
					time = Dmgcpu.getInt(bytes, 0);
					
					

					time = (time << 32);
					time += Dmgcpu.getInt(bytes, 5);
*/
					
					//TODO
					//Read in the unix timestamp we now store dif and update rtc
					
					// load real time clock
					// System.arraycopy(b, bankCount * bankSize, dmgcpu
					// .getRtcReg(), 0, 5);
					//long time = Dmgcpu.getInt(dmgcpu.rtcReg, 0);//5);
				
					System.out.println("Last played at "+ new Date(time));
					/*time = (time << 32)
							+ ((long) Dmgcpu.getInt(dmgcpu.rtcReg, 9) & 0xffffffffL);
							*/
					time = System.currentTimeMillis() - time;
					dmgcpu.rtcSkip((int) (time / 1000));
					System.out.println("Realigned RTC by " + (time / 1000)+"seconds" );
				}
			}
			rs.close();// .closeRecordStore();
			System.out.println("Ram Loaded");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
