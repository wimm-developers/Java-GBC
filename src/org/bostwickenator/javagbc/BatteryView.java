package org.bostwickenator.javagbc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.view.View;

public class BatteryView extends View {

	Bitmap plugged,battery,bars;
	
	public BatteryView(Context context) {
		super(context);
		
		plugged = BitmapFactory.decodeResource(getResources(), R.drawable.charging);
		battery = BitmapFactory.decodeResource(getResources(), R.drawable.battery);
		bars = BitmapFactory.decodeResource(getResources(), R.drawable.battery_bars);
		
	}
	
	float charge=-1;
	
	public void setCharge(float amount){
		charge = amount;
		this.postInvalidate();
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		// TODO Auto-generated method stub
		super.onDraw(canvas);
		
		canvas.drawColor(Color.WHITE, Mode.SRC);
		
		if(charge==-1){
			canvas.drawBitmap(plugged, 0,0,null);
		}else{
			canvas.drawBitmap(battery, 0,0,null);
			
			Rect dst = new Rect(0, 0, (int) (bars.getWidth()*charge), bars.getHeight());
			canvas.drawBitmap(bars, dst,dst,null);
		}
		
		
	}

}
