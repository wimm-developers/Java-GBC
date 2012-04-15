package org.bostwickenator.javagbc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.opengles.GL10;

import org.bostwickenator.javagbc.GLSurfaceView.EglHelper;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLU;
import android.opengl.GLUtils;


/**
 * Render a pair of tumbling cubes.
 */

class RomRenderer implements GLSurfaceView.Renderer {

	GL10 gl;
	EglHelper mEglHelper;

	public void runRom(GL10 gl, EglHelper mEglHelper) {
		this.gl = gl;
		this.mEglHelper = mEglHelper;
		//RomController.runRom(this);
	}

	public int[] getConfigSpec() {
		// int[] configSpec = { EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE,
		// 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
		// EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_NONE };

		return new int[] { EGL10.EGL_ALPHA_SIZE, 0, EGL10.EGL_DEPTH_SIZE, 0,
				EGL10.EGL_NONE };
	}

	public void sizeChanged(GL10 gl, int width, int height) {

		//boolean fullscreen = KiddGBC.prefs.getBoolean("fullscreen", false);
		//boolean touch = KiddGBC.prefs.getBoolean("touch", false);

		gl.glViewport(0, 0, width, height);
		//surfaceCreated(gl);

		// if (touch) {
		// GLU.gluOrtho2D(gl, width, 0, 0, height);
		// } else {
		GLU.gluOrtho2D(gl, 0, width, height, 0);
		// }
		System.out.println(width + " " + height);
/*
		int x = 0, y = 0, newWidth, newHeight;
		double sizeFactor;

		if (fullscreen) {
			newWidth = width;
			newHeight = height;
			System.out.println("FullScreen");
			if (touch) {
				newWidth -= 50;
				x += 50;
				System.out.println("Touch");
			}
		} else {
			System.out.println("Not Fullscreen");
			if (width < height)
				sizeFactor = width / 160.0;
			else
				sizeFactor = height / 144.0;
			sizeFactor=1;
			newWidth = (int) (160 * sizeFactor);
			newHeight = (int) (144 * sizeFactor);
			System.out.println(width + " " + height + " " + sizeFactor + " "
					+ newWidth + " " + newHeight);
			if (touch) {
				System.out.println("Touch");
				x = (width - 50 - newWidth) / 2;
				x += 50;
			} else {
				x = (width - newWidth) / 2;
			}
		}

		square[0] = (short) x;
		square[1] = 0;
		square[2] = (short) (x + newWidth);
		square[3] = 0;
		square[4] = (short) x;
		square[5] = (short) (newHeight);
		square[6] = (short) (x + newWidth);
		square[7] = (short) (newHeight);

		
		
		ByteBuffer byteBuf = ByteBuffer.allocateDirect(square.length * 2);
		byteBuf.order(ByteOrder.nativeOrder());
		vertBuffer = byteBuf.asShortBuffer();
		vertBuffer.put(square);
		vertBuffer.position(0);
		
		byteBuf = ByteBuffer.allocateDirect(texCoords.length * 4);
		byteBuf.order(ByteOrder.nativeOrder());
		texBuffer = byteBuf.asFloatBuffer();
		texBuffer.put(texCoords);
		texBuffer.position(0);
	
		gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, texBuffer);
		gl.glVertexPointer(2, GL10.GL_SHORT, 0, vertBuffer);
	*/
		
	}

	short square[] = { 0, 0, 160, 0, 0, 144, 160, 144 };

	public void surfaceCreated(GL10 gl) {
		
		firstTexture = true;
		
		ByteBuffer byteBuf = ByteBuffer.allocateDirect(square.length * 2);
		byteBuf.order(ByteOrder.nativeOrder());
		vertBuffer = byteBuf.asShortBuffer();
		vertBuffer.put(square);
		vertBuffer.position(0);
		
		byteBuf = ByteBuffer.allocateDirect(texCoords.length * 4);
		byteBuf.order(ByteOrder.nativeOrder());
		texBuffer = byteBuf.asFloatBuffer();
		texBuffer.put(texCoords);
		texBuffer.position(0);

		gl.glEnable(GL10.GL_TEXTURE_2D);
		
		gl.glDisable(GL10.GL_DITHER);
		gl.glClear(GL10.GL_DEPTH_BUFFER_BIT);
		gl.glDisable(GL10.GL_DEPTH_TEST);
		gl.glDisable(GL10.GL_CULL_FACE);
		gl.glDisable(GL10.GL_BLEND);
		gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_FASTEST);

		gl.glMatrixMode(GL10.GL_PROJECTION);
		gl.glLoadIdentity();

		gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, texBuffer);
		gl.glVertexPointer(2, GL10.GL_SHORT, 0, vertBuffer);
		gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
		gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);

		gl.glBindTexture(GL10.GL_TEXTURE_2D, 1);

		gl.glPixelStorei(GL10.GL_UNPACK_ALIGNMENT, 1);
		gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER,
				GL10.GL_NEAREST);
		gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER,
				GL10.GL_NEAREST);
		gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE,
				GL10.GL_REPLACE);// .GL_DECAL);
		
		gl.glClearColor(0, 0, 0, 1);
	}

	/*
	public void clearTo(int color) {
		gl.glClear(GL10.GL_COLOR_BUFFER_BIT);
	}

	public void clearTo(int color, int x, int y, int width, int height) {
		gl.glClear(GL10.GL_COLOR_BUFFER_BIT);
	}
	*/

	float texCoords[] = { 0.0f, 0.0f, 159f / 255f, 0.0f, 0.0f, 143f / 255f,
			159f / 255f, 143f / 255f };

	ShortBuffer vertBuffer ;//= ShortBuffer.wrap(square);
	FloatBuffer texBuffer;// = FloatBuffer.wrap(texCoords);

	//private static final int internalFormat = GL10.GL_TEXTURE_2D;

	boolean firstTexture = true;
	
	public void restartTextures(){
		firstTexture=true;
	}
	
	
	public void drawToScreen(Bitmap image) {

		
		//gl.glClear(GL10.GL_COLOR_BUFFER_BIT);
		
	//	if(firstTexture){
			GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, GL10.GL_RGB, image, 0);
		//	firstTexture=false;
		//}else{
		//	GLUtils.texSubImage2D(GL10.GL_TEXTURE_2D, 0, 0, 0, image);
		//}
		gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4);
		gl.glFlush();
		mEglHelper.swap();
		
		mRomView.drawFPS();
		// System.out.println("Drawn to screen");
	}
	
	RomView mRomView;
	
	public RomRenderer(RomView view) {
		mRomView =view;
	}
	
}