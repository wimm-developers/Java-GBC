package org.bostwickenator.javagbc;

/*


 JavaBoy

 COPYRIGHT (C) 2001 Neil Millstone and The Victoria University of Manchester
 ;;;
 This program is free software; you can redistribute it and/or modify it
 under the terms of the GNU General Public License as published by the Free
 Software Foundation; either version 2 of the License, or (at your option)
 any later version.        

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 more details.


 You should have received a copy of the GNU General Public License along with
 this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 Place - Suite 330, Boston, MA 02111-1307, USA.

 */

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

/**
 * This is the central controlling class for the sound. It interfaces with the
 * Java Sound API, and handles the calsses for each sound channel.
 */
class SoundChip {
	/** The DataLine for outputting the sound */
	AudioTrack soundLine;

	SquareWaveGenerator channel1;
	SquareWaveGenerator channel2;
	VoluntaryWaveGenerator channel3;
	NoiseGenerator channel4;
	boolean soundEnabled = false;

	/** If true, channel is enabled */
	boolean channel1Enable = true, channel2Enable = true,
			channel3Enable = true, channel4Enable = true;

	/** Current sampling rate that sound is output at */
	int sampleRate = 44100;

	/** Amount of sound data to buffer before playback */
	int bufferLengthMsec = 200;

	/** Initialize sound emulation, and allocate sound hardware */
	public SoundChip() {
		//soundLine = initSoundHardware();
		channel1 = new SquareWaveGenerator(sampleRate);
		channel2 = new SquareWaveGenerator(sampleRate);
		channel3 = new VoluntaryWaveGenerator(sampleRate);
		channel4 = new NoiseGenerator(sampleRate);
		
		mSoundWriter.start();
	}

	Handler hand;
	
	SoundWriter mSoundWriter = new SoundWriter();
	
	public void stop(){
		
		System.out.println("Stopping sound system");
		 if(soundLine!=null)
		 {
			 try{
				soundLine.stop();
				soundLine.release();
			 }catch (Exception e) {
				e.printStackTrace();
			}
		 }
		 
		 mSoundWriter.requestStop();
		
	}
	
	int minSize;
	
	/** Initialize sound hardware if available */
	public AudioTrack initSoundHardware() {

		try {
			

			minSize = AudioTrack.getMinBufferSize(sampleRate,
					AudioFormat.CHANNEL_CONFIGURATION_STEREO,
					AudioFormat.ENCODING_PCM_8BIT);
			

			if (minSize == AudioTrack.ERROR || minSize == AudioTrack.ERROR_BAD_VALUE) {
				System.out.println("Error: Can't find audio output system!");
				soundEnabled = false;
			}
			else{
				minSize *=2;
				
				System.out.println("Audio buffer size " + minSize);
				
				AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
						AudioFormat.CHANNEL_CONFIGURATION_STEREO,
						AudioFormat.ENCODING_PCM_8BIT, minSize,
						AudioTrack.MODE_STREAM);
				
				track.play();
				soundEnabled = true;
				return track;
			}
			
			

			// DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class,
			// format);
/*
			if (!AudioSystem.isLineSupported(lineInfo)) {
				System.out.println("Error: Can't find audio output system!");
				soundEnabled = false;
			} else {
				SourceDataLine line = (SourceDataLine) AudioSystem
						.getLine(lineInfo);

				int bufferLength = (sampleRate / 1000) * bufferLengthMsec;
				line.open(format, bufferLength);
				line.start();
				// System.out.println("Initialized audio successfully.");
				soundEnabled = true;
				return line;
			}
			*/
		} catch (Exception e) {
			System.out.println("Error: Audio system busy!");
			soundEnabled = false;
		}

		return null;
	}

	/** Change the sample rate of the playback */
	public void setSampleRate(int sr) {
		sampleRate = sr;

		soundLine.flush();
		soundLine.release();// .close();

		soundLine = initSoundHardware();

		channel1.setSampleRate(sr);
		channel2.setSampleRate(sr);
		channel3.setSampleRate(sr);
		channel4.setSampleRate(sr);
	}

	/** Change the sound buffer length */
	public void setBufferLength(int time) {
		bufferLengthMsec = time;

		soundLine.flush();
		soundLine.release();
		// soundLine.close();

		soundLine = initSoundHardware();
	}
	

	int numSamples  =((sampleRate / 28) & 0xFFFE);
	byte[] b = new byte[numSamples];

	
	public static void bytefill(byte[] array, byte value) {
		 int len = array.length;
		 if (len > 0)
		 array[0] = value;
		  for (int i = 1; i < len; i += i) {
		    System.arraycopy( array, 0, array, i, ((len - i) < i) ? (len - i) : i);
		 }
		}
	
	
	/** Adds a single frame of sound data to the buffer */
	public void outputSound() {
		
		if(soundLine==null)
			soundLine= initSoundHardware();
		
		if (soundEnabled) {
			
			if(mSoundWriter.willDrop())
				return;
				
			//bytefill(b, (byte)0);
			final byte[] b = new byte[numSamples];
		
			
			if (channel1Enable)
				channel1.play(b, numSamples / 2, 0);
			if (channel2Enable)
				channel2.play(b, numSamples / 2, 0);
			
			if (channel3Enable)
				channel3.play(b, numSamples / 2, 0);
			if (channel4Enable)
				channel4.play(b, numSamples / 2, 0);
			
			//soundLine.pause();
			
			//soundLine.flush();
			
			//soundLine.write(b, 0, numSamples);
		
			
			mSoundWriter.post(new Runnable() {
				
				@Override
				public void run() {
					// TODO Auto-generated method stub
					soundLine.write(b, 0, numSamples);
				}
			});
			/*
			hand.post(new Runnable() {
				
				@Override
				public void run() {
					// TODO Auto-generated method stub
					soundLine.flush();
					soundLine.write(b, 0, numSamples);
				}
			});*/
			
		}
	}

	public class SoundWriter extends Thread{
		
		int inQueue;
		
		@Override
		public void run() {
			try {
			    // preparing a looper on current thread
			    // the current thread is being detected implicitly
			    Looper.prepare();
			 
			    hand = new Handler();
			   
			    Looper.loop();
			  } catch (Throwable t) {
			    
			  }
			
		}
		
		// This method is allowed to be called from any thread
		public synchronized void requestStop() {
		
			hand.post(new Runnable() {
				@Override
				public void run() {
					// so we can use myLooper() to get its looper
					System.out.println( "SoundWriter loop quitting by request");
					
					Looper.myLooper().quit();
				}
			});
		}
		
		
		public synchronized boolean willDrop(){
			return inQueue>=1;
		}
		
		
		public synchronized void post(final Runnable toRun){
			
			if(inQueue>=1)
				return;
			
			inQueue++;
			hand.post(new Runnable() {
				
				@Override
				public void run() {
					toRun.run();
					inQueue--;
				}
			});
		}
		
	};
	
	
	/*
	Runnable writeSound = new Runnable() {
	
		
		@Override
		public void run() {
			soundLine.write(b, 0, numSamples);
		}
	};
	*/
}
