package org.bostwickenator.javagbc;

import org.bostwickenator.javagbc.R;

import com.wimm.framework.app.FontManager;
import com.wimm.framework.app.LauncherActivity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class RomView extends LauncherActivity implements OnTouchListener, SensorEventListener {

	//static RomView instance;
	
	private boolean hidetop;
	//private GLSurfaceView surface;
	private RomSurfaceView surface;
	private int romId;
	private String romName, romPath;
	private boolean touch, drawFPS;
	
	BatteryView mBatteryView;
	//ProgressBar battery;
	
	TextView fps;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		//setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		
		//instance = this;

		romId = this.getIntent().getIntExtra("romId", -1);
		romName = this.getIntent().getStringExtra("romName");
		romPath = this.getIntent().getStringExtra("romPath");

		
		//hidetop = KiddGBC.prefs.getBoolean("hidetop", false);
	//	if (hidetop) {
			requestWindowFeature(Window.FEATURE_NO_TITLE);
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);			//Keep the phone from sleeping 
		//}

		drawFPS = KiddGBC.prefs.getBoolean("drawFPS", false);

		setContentView(R.layout.game_main);
		
		
		mBatteryView = new BatteryView(this);

		((TextView)findViewById(R.id.TextViewTime)).setTypeface(Typeface.createFromAsset(getAssets(), "clock.ttf"));
		
		((ViewGroup)findViewById(R.id.BarBattery)).addView(mBatteryView);
		//battery.setMax(100);
		
		fps = (TextView) findViewById(R.id.FPS);
		if(drawFPS){
			fps.setVisibility(View.VISIBLE);
		}
		
		//surface = new GLSurfaceView(this);
		//surface.setRenderer(new RomRenderer(this));
		surface = new RomSurfaceView(this);
	
		((ViewGroup)findViewById(R.id.SurfaceHolder)).addView(surface);
		//this.setContentView(surface);
		/*touch = KiddGBC.prefs.getBoolean("touch", false);
		if (touch) {
			
		}*/
		
		addTouchControls();
		
		
		mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
	    mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
	    
	    batteryLevel();
		
	}
	
	 private SensorManager mSensorManager;
     private Sensor mAccelerometer;

     BroadcastReceiver batteryLevelReceiver;
     
     private void batteryLevel() {
         batteryLevelReceiver = new BroadcastReceiver() {
             public void onReceive(Context context, Intent intent) {
               
                 float rawlevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                 int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                 int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                 
                 if(plugged != 0 ){
                	 mBatteryView.setCharge(-1);
                 }else{
                 
	                 float level = -1;
	                 if (rawlevel >= 0 && scale > 0) {
	                     level = rawlevel  / scale;
	                 }
	                 mBatteryView.setCharge(level);
                 }
               
                 
                 //batterLevel.setText("Battery Level Remaining: " + level + "%");
             }
         };
         IntentFilter batteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
         registerReceiver(batteryLevelReceiver, batteryLevelFilter);
     }
      

	private void addTouchControls() {
		findViewById(R.id.ButtonA).setOnTouchListener(this);
		findViewById(R.id.ButtonB).setOnTouchListener(this);
		findViewById(R.id.ButtonSelect).setOnTouchListener(this);
		findViewById(R.id.ButtonStart).setOnTouchListener(this);
		
	}

	@Override
	protected void onStart() {
		super.onStart();
		surface.runRom();
	}

	private int[] previousTime = new int[16];
	private int previousTimeIx;
	private int estfps;

	public void drawFPS() {
		/*
		if (hidetop || !drawFPS)
			return;
		int now = (int) System.currentTimeMillis();
		// calculate moving-average fps
		// 17 ms * 60 fps * 2*16 seconds = 32640 ms
		estfps = ((32640 + now - previousTime[previousTimeIx]) / (now - previousTime[previousTimeIx])) >> 1;
		previousTime[previousTimeIx] = now;
		previousTimeIx = (previousTimeIx + 1) & 0x0F;

		runOnUiThread(setTitle);
		*/
	}

	Runnable setTitle = new Runnable() {
		
		@Override
		public void run() {
			fps.setText(""+ estfps + "fps");
		}
	};
	

	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (touch && keyCode == KeyEvent.KEYCODE_BACK) {
			RomController.keyDown(touch, RomController.key[7]);
			return true;
		} else if (touch && keyCode == KeyEvent.KEYCODE_CALL) {
			RomController.keyDown(touch, RomController.key[6]);
			return true;
		}
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			GLSurfaceView.mGLThread.requestExitAndWait();
			finish();
		}
		super.onKeyDown(keyCode, event);
		RomController.keyDown(touch, keyCode);
		return false;
	}

	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (touch && keyCode == KeyEvent.KEYCODE_BACK) {
			RomController.keyUp(RomController.key[7]);
			return true;
		} else if (touch && keyCode == KeyEvent.KEYCODE_CALL) {
			RomController.keyUp(RomController.key[6]);
			return true;
		}
		super.onKeyUp(keyCode, event);
		RomController.keyUp(keyCode);
		return false;
	}

	@Override
	protected void onResume() {
		super.onResume();

		Dmgcpu.running = true;
		//surface.onResume();
		
		 mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
	}

	@Override
	protected void onPause() {
		super.onPause();
		
		 mSensorManager.unregisterListener(this);

		System.out.println("Paused");
		Dmgcpu.running = false;
		//surface.onPause();
		try{
			unregisterReceiver(batteryLevelReceiver);
		}catch (Exception e) {
			e.printStackTrace();
		}
		//RomRenderer.
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		//GLSurfaceView.mGLThread.requestExitAndWait();
		surface.run = false;
		RomController.stopDmgCPU();
		
		//System.exit(1);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, 1, 0, "Save");
		menu.add(0, 2, 0, "Load");
		menu.add(0, 3, 0, "End Game");
		System.out.println("Created options menu");
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		Dmgcpu.running = false;
		//surface.onPause();
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public void onOptionsMenuClosed(Menu menu) {
		Dmgcpu.running = true;
		//surface.onResume();
		super.onOptionsMenuClosed(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
		case 1:
			Intent i = new Intent(RomView.this, SaveStates.class);
			i.putExtra("type", "save");
			i.putExtra("romId", romId);
			i.putExtra("romName", romName);
			i.putExtra("romPath", romPath);
			System.out.println("Save State Activity");
			startActivityForResult(i, 1);
			return true;
		case 2:
			Intent j = new Intent(RomView.this, SaveStates.class);
			j.putExtra("type", "load");
			j.putExtra("romId", romId);
			j.putExtra("romName", romName);
			j.putExtra("romPath", romPath);
			startActivityForResult(j, 2);
			return true;
		case 3:
			GLSurfaceView.mGLThread.requestExitAndWait();
			finish();
		}
		return false;
	}

	/*
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode != Activity.RESULT_OK)
			return;
		if (data.getStringExtra("save").equals("load")) {
			GLSurfaceView.mGLThread.requestExitAndWait();
			romId = data.getIntExtra("romId", -1);
			romName = data.getStringExtra("romName");
			romPath = data.getStringExtra("romPath");
			RomController.loadRom(data.getStringExtra("state"));

			surface = new GLSurfaceView(this);
			surface.setRenderer(new RomRenderer(this));
			this.setContentView(surface);
			if (touch) {
				addTouchControls();
			}
			surface.runRom();
		}
	}
	*/

	
	
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		
		
		int keycode=0;
		
		int id = v.getId();
		
		
		switch (id) {
			case R.id.ButtonA:
				keycode = KeyEvent.KEYCODE_Z;
				break;
			case R.id.ButtonB:
				keycode = KeyEvent.KEYCODE_X;
				break;
			case R.id.ButtonSelect:
				keycode = KeyEvent.KEYCODE_DEL;
				break;
			case R.id.ButtonStart:
				keycode = KeyEvent.KEYCODE_ENTER;
				break;
			default:
				break;
		}

		
		if(event.getAction() == MotionEvent.ACTION_DOWN){
			RomController.keyDown(false, keycode);
		}
		else if(event.getAction() == MotionEvent.ACTION_UP){
			RomController.keyUp( keycode);
		}
			
			
		return true;
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
		
	}

	static final float thresh = .3f;
	boolean dpadDown[] = new boolean[4];
	
	
	@Override
	public void onSensorChanged(SensorEvent event) {
		float x = event.values[0] / SensorManager.GRAVITY_EARTH;
		float y = event.values[1] / SensorManager.GRAVITY_EARTH;
		float z =  event.values[1] / SensorManager.GRAVITY_EARTH;
		
		boolean invert = z<-.2;
		
		boolean up = y < -thresh;
		boolean down = y > thresh;
		
		/*
		if (invert){
			up = !up;
			down = !down;
		}
		*/
	
		
		if(x < -thresh){
		
			if(dpadDown[0]==false){
				RomController.keyDown(false, KeyEvent.KEYCODE_DPAD_RIGHT);
				dpadDown[0]=true;
				//System.out.println("RIGHT");
			}
		}else{
			if(dpadDown[0]==true){
				RomController.keyUp( KeyEvent.KEYCODE_DPAD_RIGHT);
				dpadDown[0]=false;
			}
		}
		
		if(x > thresh){
			if(dpadDown[1]==false){
				RomController.keyDown(false, KeyEvent.KEYCODE_DPAD_LEFT);
				dpadDown[1]=true;
			}
		}else{
			if(dpadDown[1]==true){
				RomController.keyUp( KeyEvent.KEYCODE_DPAD_LEFT);
				dpadDown[1]=false;
			}
		}
		
		if(up){
			if(dpadDown[2]==false){
				RomController.keyDown(false, KeyEvent.KEYCODE_DPAD_UP);
				dpadDown[2]=true;
			}
		}else{
			if(dpadDown[2]==true){
				RomController.keyUp( KeyEvent.KEYCODE_DPAD_UP);
				dpadDown[2]=false;
				
			}
		}
		
		
		if(down){
			if(dpadDown[3]==false){
				RomController.keyDown(false, KeyEvent.KEYCODE_DPAD_DOWN);
				dpadDown[3]=true;
			}
		}else{
			if(dpadDown[3]==true){
				RomController.keyUp( KeyEvent.KEYCODE_DPAD_DOWN);
				dpadDown[3]=false;
			}
		}
			
	}
}
