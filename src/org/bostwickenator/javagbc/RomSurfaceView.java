package org.bostwickenator.javagbc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Process;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class RomSurfaceView extends SurfaceView {

	SurfaceHolder mSurfaceHolder;
	
	Rect src;
	
	public RomSurfaceView(Context context) {
		super(context);

		mSurfaceHolder = getHolder();
		
		//mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		
		//src = new Rect(0, 0, 160, 144);
	
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		
		
		//super.onDraw(canvas);
		try{
		if(currentFrame!=null)
			canvas.drawBitmap(currentFrame,0,0,null);//src,src,null);
		}catch (Exception e) {
			System.out.println("LOST FRAME");
			e.printStackTrace();
			
		}
		
	}
	
	Bitmap currentFrame;
	
	boolean run=true;
	
	
	public void runRom(){
		Thread t = new Thread(new Runnable() {
			
			@Override
			public void run() {

				//Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);
				//while(run){
					
					RomController.runRom(RomSurfaceView.this);
				//}
							
			}
		});
		//t.setPriority(Thread.MAX_PRIORITY);
		t.setName("GameThread");
		t.start();
	}
	
	
	public void drawToScreen(Bitmap image) {
		//System.out.println("s"+System.currentTimeMillis());
		currentFrame= image;
		 Canvas c = null;
		  try {
              c = mSurfaceHolder.lockCanvas(null);
              synchronized (mSurfaceHolder) {
                  onDraw(c); //draw canvas 
                 
              }
          } finally {
              if (c != null) {
                  mSurfaceHolder.unlockCanvasAndPost(c);  //show canvas
              }
          }
		 // System.out.println("e"+System.currentTimeMillis());
	}

}
