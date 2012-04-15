package org.bostwickenator.javagbc;

import org.bostwickenator.javagbc.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.DialogInterface.OnKeyListener;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class Settings extends Activity implements OnClickListener{

	private Button aButton, bButton, upButton, downButton, leftButton,
			rightButton, startButton, selectButton;
	private CheckBox hideTop, fullscreen, touchControls, drawFPS;
	private Spinner thumbnails;

	private SharedPreferences pref;

	private int selectedButton = -1;

	@Override
	/* Initialize JavaBoy when run as an application */
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settings);
		pref = getSharedPreferences("com.teragadgets.android.gameboy", 0);

		
		aButton = (Button) this.findViewById(R.id.a);
		aButton.setOnClickListener(this);
		
		bButton = (Button) this.findViewById(R.id.b);
		bButton.setOnClickListener(this);
		
		upButton = (Button) this.findViewById(R.id.up);
		upButton.setOnClickListener(this);
		
		downButton = (Button) this.findViewById(R.id.down);
		downButton.setOnClickListener(this);
		
		leftButton = (Button) this.findViewById(R.id.left);
		leftButton.setOnClickListener(this);
		
		rightButton = (Button) this.findViewById(R.id.right);
		rightButton.setOnClickListener(this);
		
		startButton = (Button) this.findViewById(R.id.start);
		startButton.setOnClickListener(this);
		
		selectButton = (Button) this.findViewById(R.id.select);
		selectButton.setOnClickListener(this);
		
		thumbnails = (Spinner) this.findViewById(R.id.thumbnails);
		thumbnails.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int position, long id) {
				SharedPreferences.Editor editor = pref.edit();
				editor.putInt("thumbnails", position);
				editor.commit();
				KiddGBC.thumbs.clear();
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});

		fullscreen = (CheckBox) this.findViewById(R.id.fullscreen);
		fullscreen.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				SharedPreferences.Editor editor = pref.edit();
				if (isChecked)
					editor.putBoolean("fullscreen", true);
				else
					editor.putBoolean("fullscreen", false);
				editor.commit();
			}
		});

		hideTop = (CheckBox) this.findViewById(R.id.hidetop);
		hideTop.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				SharedPreferences.Editor editor = pref.edit();
				if (isChecked)
					editor.putBoolean("hidetop", true);
				else
					editor.putBoolean("hidetop", false);
				editor.commit();
			}
		});

		drawFPS = (CheckBox) this.findViewById(R.id.drawfps);
		drawFPS.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				SharedPreferences.Editor editor = pref.edit();
				if (isChecked)
					editor.putBoolean("drawFPS", true);
				else
					editor.putBoolean("drawFPS", false);
				editor.commit();
			}
		});
		
		
		CheckBox sound = (CheckBox) this.findViewById(R.id.sound);
		sound.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				SharedPreferences.Editor editor = pref.edit();
				if (isChecked)
					editor.putBoolean("sound", true);
				else
					editor.putBoolean("sound", false);
				editor.commit();
			}
		});
		
		touchControls = (CheckBox) this.findViewById(R.id.touch);
		touchControls.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				SharedPreferences.Editor editor = pref.edit();
				if (isChecked) {
					editor.putBoolean("touch", true);
					aButton.setEnabled(false);
					bButton.setEnabled(false);
					upButton.setEnabled(false);
					downButton.setEnabled(false);
					leftButton.setEnabled(false);
					rightButton.setEnabled(false);
					startButton.setEnabled(false);
					selectButton.setEnabled(false);
				} else {
					editor.putBoolean("touch", false);
					aButton.setEnabled(true);
					bButton.setEnabled(true);
					upButton.setEnabled(true);
					downButton.setEnabled(true);
					leftButton.setEnabled(true);
					rightButton.setEnabled(true);
					startButton.setEnabled(true);
					selectButton.setEnabled(true);
				}
				editor.commit();
			}
		});

		thumbnails.setSelection(pref.getInt("thumbnails", 0));
		hideTop.setChecked(pref.getBoolean("hidetop", false));
		fullscreen.setChecked(pref.getBoolean("fullscreen", false));
		drawFPS.setChecked(pref.getBoolean("drawFPS", false));
		touchControls.setChecked(pref.getBoolean("touch", false));
		aButton.setText(getKeyName(pref.getInt("aButton", KeyEvent.KEYCODE_Z)));
		bButton.setText(getKeyName(pref.getInt("bButton", KeyEvent.KEYCODE_X)));
		sound.setChecked(pref.getBoolean("sound", true));
		
		upButton.setText(getKeyName(pref.getInt("upButton",
				KeyEvent.KEYCODE_DPAD_UP)));
		downButton.setText(getKeyName(pref.getInt("downButton",
				KeyEvent.KEYCODE_DPAD_DOWN)));
		leftButton.setText(getKeyName(pref.getInt("leftButton",
				KeyEvent.KEYCODE_DPAD_LEFT)));
		rightButton.setText(getKeyName(pref.getInt("rightButton",
				KeyEvent.KEYCODE_DPAD_RIGHT)));
		
		startButton.setText(getKeyName(pref.getInt("startButton",
				KeyEvent.KEYCODE_ENTER)));
		selectButton.setText(getKeyName(pref.getInt("selectButton",
				KeyEvent.KEYCODE_DEL)));
	}

	/*
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		
		//if (keyCode == KeyEvent.KEYCODE_BACK)
		//	return false;
		if(selectedButton==-1)
			return super.onKeyDown(keyCode, event);
		
		
		
		return true;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		
		
		if(selectedButton!=-1){
			selectedButton = -1;
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}
	*/

	public static String getKeyName(int code) {
	
		
		String labelString="??";
		switch(code){
		
		  	case KeyEvent.KEYCODE_UNKNOWN          : labelString="??"; break;
		    case KeyEvent.KEYCODE_SOFT_LEFT        : labelString="Left-Soft"; break;
		    case KeyEvent.KEYCODE_SOFT_RIGHT       : labelString="Right-Soft"; break;
		    case KeyEvent.KEYCODE_HOME             : labelString="Home"; break;
		    case KeyEvent.KEYCODE_BACK             : labelString="Back"; break;
		    case KeyEvent.KEYCODE_CALL             : labelString="Call"; break;
		    case KeyEvent.KEYCODE_ENDCALL          : labelString="End Call"; break;
		    case KeyEvent.KEYCODE_0                : labelString="0"; break;
		    case KeyEvent.KEYCODE_1                : labelString="1"; break;
		    case KeyEvent.KEYCODE_2                : labelString="2"; break;
		    case KeyEvent.KEYCODE_3                : labelString="3"; break;
		    case KeyEvent.KEYCODE_4                : labelString="4"; break;
		    case KeyEvent.KEYCODE_5                : labelString="5"; break;
		    case KeyEvent.KEYCODE_6                : labelString="6"; break;
		    case KeyEvent.KEYCODE_7                : labelString="7"; break;
		    case KeyEvent.KEYCODE_8                : labelString="8"; break;
		    case KeyEvent.KEYCODE_9                : labelString="9"; break;
		    case KeyEvent.KEYCODE_STAR             : labelString="*"; break;
		    case KeyEvent.KEYCODE_POUND            : labelString="£"; break;
		    case KeyEvent.KEYCODE_DPAD_UP          : labelString="D-Up"; break;
		    case KeyEvent.KEYCODE_DPAD_DOWN        : labelString="D-Down"; break;
		    case KeyEvent.KEYCODE_DPAD_LEFT        : labelString="D-Left"; break;
		    case KeyEvent.KEYCODE_DPAD_RIGHT       : labelString="D-Right"; break;
		    case KeyEvent.KEYCODE_DPAD_CENTER      : labelString="D-Center"; break;
		    case KeyEvent.KEYCODE_VOLUME_UP        : labelString="Vol-Up"; break;
		    case KeyEvent.KEYCODE_VOLUME_DOWN      : labelString="Vol-Down"; break;
		    case KeyEvent.KEYCODE_POWER            : labelString="Power"; break;
		    case KeyEvent.KEYCODE_CAMERA           : labelString="Camera"; break;
		    case KeyEvent.KEYCODE_CLEAR            : labelString="Clear"; break;
		    case KeyEvent.KEYCODE_A                : labelString="A"; break;
		    case KeyEvent.KEYCODE_B                : labelString="B"; break;
		    case KeyEvent.KEYCODE_C                : labelString="C"; break;
		    case KeyEvent.KEYCODE_D                : labelString="D"; break;
		    case KeyEvent.KEYCODE_E                : labelString="E"; break;
		    case KeyEvent.KEYCODE_F                : labelString="F"; break;
		    case KeyEvent.KEYCODE_G                : labelString="G"; break;
		    case KeyEvent.KEYCODE_H                : labelString="H"; break;
		    case KeyEvent.KEYCODE_I                : labelString="I"; break;
		    case KeyEvent.KEYCODE_J                : labelString="J"; break;
		    case KeyEvent.KEYCODE_K                : labelString="K"; break;
		    case KeyEvent.KEYCODE_L                : labelString="L"; break;
		    case KeyEvent.KEYCODE_M                : labelString="M"; break;
		    case KeyEvent.KEYCODE_N                : labelString="N"; break;
		    case KeyEvent.KEYCODE_O                : labelString="O"; break;
		    case KeyEvent.KEYCODE_P                : labelString="P"; break;
		    case KeyEvent.KEYCODE_Q                : labelString="Q"; break;
		    case KeyEvent.KEYCODE_R                : labelString="R"; break;
		    case KeyEvent.KEYCODE_S                : labelString="S"; break;
		    case KeyEvent.KEYCODE_T                : labelString="T"; break;
		    case KeyEvent.KEYCODE_U                : labelString="U"; break;
		    case KeyEvent.KEYCODE_V                : labelString="V"; break;
		    case KeyEvent.KEYCODE_W                : labelString="W"; break;
		    case KeyEvent.KEYCODE_X                : labelString="X"; break;
		    case KeyEvent.KEYCODE_Y                : labelString="Y"; break;
		    case KeyEvent.KEYCODE_Z                : labelString="Z"; break;
		    case KeyEvent.KEYCODE_COMMA            : labelString=","; break;
		    case KeyEvent.KEYCODE_PERIOD           : labelString="."; break;
		    case KeyEvent.KEYCODE_ALT_LEFT         : labelString="L-Alt"; break;
		    case KeyEvent.KEYCODE_ALT_RIGHT        : labelString="R-Alt"; break;
		    case KeyEvent.KEYCODE_SHIFT_LEFT       : labelString="L-Shift"; break;
		    case KeyEvent.KEYCODE_SHIFT_RIGHT      : labelString="R-Shift"; break;
		    case KeyEvent.KEYCODE_TAB              : labelString="Tab"; break;
		    case KeyEvent.KEYCODE_SPACE            : labelString="Space"; break;
		    case KeyEvent.KEYCODE_SYM              : labelString="Sym"; break;
		    case KeyEvent.KEYCODE_EXPLORER         : labelString="Explorer"; break;
		    case KeyEvent.KEYCODE_ENVELOPE         : labelString="Envelope"; break;
		    case KeyEvent.KEYCODE_ENTER            : labelString="Enter"; break;
		    case KeyEvent.KEYCODE_DEL              : labelString="Del"; break;
		    case KeyEvent.KEYCODE_GRAVE            : labelString="`"; break;
		    case KeyEvent.KEYCODE_MINUS            : labelString="-"; break;
		    case KeyEvent.KEYCODE_EQUALS           : labelString="="; break;
		    case KeyEvent.KEYCODE_LEFT_BRACKET     : labelString="("; break;
		    case KeyEvent.KEYCODE_RIGHT_BRACKET    : labelString=")"; break;
		    case KeyEvent.KEYCODE_BACKSLASH        : labelString="\\"; break;
		    case KeyEvent.KEYCODE_SEMICOLON        : labelString=";"; break;
		    case KeyEvent.KEYCODE_APOSTROPHE       : labelString="'"; break;
		    case KeyEvent.KEYCODE_SLASH            : labelString="/"; break;
		    case KeyEvent.KEYCODE_AT               : labelString="@"; break;
		    case KeyEvent.KEYCODE_NUM              : labelString="Num"; break;
		    case KeyEvent.KEYCODE_HEADSETHOOK      : labelString="Headset"; break;
		    case KeyEvent.KEYCODE_FOCUS            : labelString="Camera-Focus"; break;   // *Camera* focus
		    case KeyEvent.KEYCODE_PLUS             : labelString="+"; break;
		    case KeyEvent.KEYCODE_MENU             : labelString="Menu"; break;
		    case KeyEvent.KEYCODE_NOTIFICATION     : labelString="Notification"; break;
		    case KeyEvent.KEYCODE_SEARCH           : labelString="Search"; break;
		
		}
		return labelString;
	}

	AlertDialog dia;
	@Override
	public void onClick(View v) {
		int id = v.getId();
		
		
		
		String toastFilling ="";
		
		switch(id){
		case R.id.a:
			selectedButton = 0;
			toastFilling="A";
			break;
		case R.id.b:
			selectedButton = 1;
			toastFilling="B";
			break;
		case R.id.up:
			selectedButton = 2;
			toastFilling="Up";
			break;
		case R.id.down:
			selectedButton = 3;
			toastFilling="Down";
			break;
		case R.id.left:
			selectedButton = 4;
			toastFilling="Left";
			break;
		case R.id.right:
			selectedButton = 5;
			toastFilling="Right";
			break;
		case R.id.start:
			selectedButton = 6;
			toastFilling="Start";
			break;
		case R.id.select:
			selectedButton = 7;
			toastFilling="Select";
			break;
		}
		
		AlertDialog.Builder alertBuilder = new Builder(this);
		
		alertBuilder.setTitle("Setting key for "+toastFilling);
		alertBuilder.setMessage("Press key");
		alertBuilder.setPositiveButton("Done", new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		
		
		dia = alertBuilder.create();
		
		
		dia.setOnKeyListener(new OnKeyListener() {
			
			@Override
			public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
				
				dia.setMessage("Pressed "+ getKeyName(keyCode));
				
				SharedPreferences.Editor editor = pref.edit();
				switch (selectedButton) {
					case 0:
						//System.out.println(keyCode + " " + pref.getInt("aButton", KeyEvent.KEYCODE_A));
						aButton.setText(getKeyName(keyCode));
						editor.putInt("aButton", keyCode);
						break;
					case 1:
						bButton.setText(getKeyName(keyCode));
						editor.putInt("bButton", keyCode);
						break;
					case 2:
						upButton.setText(getKeyName(keyCode));
						editor.putInt("upButton", keyCode);
						break;
					case 3:
						downButton.setText(getKeyName(keyCode));
						editor.putInt("downButton", keyCode);
						break;
					case 4:
						leftButton.setText(getKeyName(keyCode));
						editor.putInt("leftButton", keyCode);
						break;
					case 5:
						rightButton.setText(getKeyName(keyCode));
						editor.putInt("rightButton", keyCode);
						break;
					case 6:
						startButton.setText(getKeyName(keyCode));
						editor.putInt("startButton", keyCode);
						break;
					case 7:
						selectButton.setText(getKeyName(keyCode));
						editor.putInt("selectButton", keyCode);
						break;
				}
				editor.commit();
				return true;
			}
		});
		dia.show();
		
		
		
		//Toast.makeText(this,"Press new key for "+toastFilling+" button now",Toast.LENGTH_SHORT).show();
	}

}
