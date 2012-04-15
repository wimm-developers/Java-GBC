package org.bostwickenator.javagbc;

import java.util.Arrays;
import java.util.HashMap;

import org.bostwickenator.javagbc.R;

import com.wimm.framework.app.AlertDialog;
import com.wimm.framework.app.LauncherActivity;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Toast;

public class KiddGBC extends LauncherActivity implements OnClickListener,
		OnItemClickListener ,OnItemLongClickListener{

	public static int IMAGE_WIDTH = 53, IMAGE_HEIGHT = 48;

	static SharedPreferences prefs;
	ListView romListView;
	static RomDatabase database;
	RomAdapter romAdapter;
	Cursor romCursor;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {

		prefs = getSharedPreferences("com.teragadgets.android.gameboy", 0);

		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		if (database == null)
			database = new RomDatabase(this).open();
		romCursor = database.fetchAllRoms();

		String[] from = new String[] { RomDatabase.KEY_ROM_NAME,
				RomDatabase.KEY_STATE_IMAGE };
		int[] to = new int[] { R.id.romname, R.id.romimage };
		romAdapter = new RomAdapter(this, R.layout.romrow, romCursor, from, to);

		romListView = (ListView) this.findViewById(android.R.id.list);
		romListView.setAdapter(romAdapter);
		romListView.setOnItemClickListener(this);
		romListView.setOnItemLongClickListener(this);
		this.registerForContextMenu(romListView);

		Button button = (Button) this.findViewById(R.id.prevrom);
		button.setTag(-1);
		if (prefs.getString("prevrom", null) == null) {
			button.setVisibility(View.GONE);
		}
		button.setOnClickListener(this);

		
		findViewById(R.id.addRom).setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				Intent i = new Intent(KiddGBC.this, FileBrowser.class);
				startActivityForResult(i, 1);
			}
		});
		
		findViewById(R.id.settings).setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				Intent j = new Intent(KiddGBC.this, Settings.class);
				startActivityForResult(j, 3);
			}
		});
		
		findViewById(R.id.homebrew).setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				Intent j = new Intent(Intent.ACTION_VIEW, Uri.parse("http://pdroms.de/files/gameboy/"));
				startActivity(j);//ForResult(j, 3);
			}
		});
		
		
		

		
		updateSettings();
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item
				.getMenuInfo();
		romCursor.moveToPosition(menuInfo.position);
		int romId = romCursor.getInt(romCursor
				.getColumnIndex(RomDatabase.KEY_ROMID));
		String romName = romCursor.getString(romCursor
				.getColumnIndex(RomDatabase.KEY_ROM_NAME));
		String romPath = romCursor.getString(romCursor
				.getColumnIndex(RomDatabase.KEY_ROM_PATH));
		switch (item.getItemId()) {
		case 0:
			startRom(romId, romName, romPath, "");
			break;
		case 1:
			database.deleteRom(romId);
			updateList();
			break;
		}
		return super.onContextItemSelected(item);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		
		
		menu.add(0, 0, 0, "Start from Beginning");
		menu.add(0, 1, 0, "Remove Rom");
		super.onCreateContextMenu(menu, v, menuInfo);
	}

	/*
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, 0, 0, "Add Rom");
		menu.add(0, 2, 0, "Settings");
		menu.add(0, 3, 0, "Close");
		System.out.println("Created options menu");
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
		case 0:
			Intent i = new Intent(KiddGBC.this, FileBrowser.class);
			startActivityForResult(i, 1);
			return true;
		case 2:
			Intent j = new Intent(KiddGBC.this, Settings.class);
			startActivityForResult(j, 3);
			return true;
		case 3:
			finish();
			return true;
		}
		return false;
	}*/

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {

		switch (requestCode) {
		case 1:

			if (resultCode == RESULT_OK) {
				long position = database.addRom(data.getStringExtra("name"),
						data.getStringExtra("path"));
				System.out.println(position);
				database.addState((int) position, "temp");
				romCursor.requery();
				romAdapter.notifyDataSetChanged();
			}
			break;
		case 4:
			if (resultCode == RESULT_OK) {
				this.startRom(data.getIntExtra("romId", -1), data
						.getStringExtra("romName"), data
						.getStringExtra("romPath"), data
						.getStringExtra("state"));
			}
			break;
		}
		updateList();
	}

	private void updateList() {
		updateSettings();
		romCursor = database.fetchAllRoms();
		romAdapter.changeCursor(romCursor);
		romAdapter.notifyDataSetChanged();
		romListView.invalidate();
	}

	private void startRom(int romId, String romName, String romPath,
			String saveState) {
		System.out.println(romName + " " + romPath + " " + saveState);
		RomController.romId = romId;
		RomController.romFile = romPath;
		
		
		if(RomController.loadRom(saveState)==false){
			
			Toast.makeText(getBaseContext(), "Bad ROM file", Toast.LENGTH_LONG).show();
			return;
		}
		Intent i = new Intent(this, RomView.class);
		i.putExtra("romId", romId);
		i.putExtra("romName", romName);
		i.putExtra("romPath", romPath);
		startActivityForResult(i, 0);
	}

	private void updateSettings() {

		
		switch (prefs.getInt("thumbnails", 1)) {
		case 0:
			IMAGE_WIDTH = 160 / 2;
			IMAGE_HEIGHT = 144 / 2;
			break;
		case 1:
			IMAGE_WIDTH = 160 / 3;
			IMAGE_HEIGHT = 144 / 3;
			break;
		case 2:
			IMAGE_WIDTH = 160 / 4;
			IMAGE_HEIGHT = 144 / 4;
			break;
		case 3:
			IMAGE_WIDTH = 0;
			IMAGE_HEIGHT = 0;
			break;
		}

		RomController.key[0] = prefs.getInt("rightButton", KeyEvent.KEYCODE_DPAD_RIGHT);
		RomController.key[1] = prefs.getInt("leftButton", KeyEvent.KEYCODE_DPAD_LEFT);
		RomController.key[2] = prefs.getInt("upButton", KeyEvent.KEYCODE_DPAD_UP);
		RomController.key[3] = prefs.getInt("downButton", KeyEvent.KEYCODE_DPAD_DOWN);
		RomController.key[4] = prefs.getInt("aButton", KeyEvent.KEYCODE_Z);
		RomController.key[5] = prefs.getInt("bButton", KeyEvent.KEYCODE_X);
		RomController.key[6] = prefs.getInt("selectButton",
				KeyEvent.KEYCODE_DEL);
		RomController.key[7] = prefs.getInt("startButton",
				KeyEvent.KEYCODE_ENTER);
		
		
		TextView text = (TextView) this.findViewById(android.R.id.empty);
		if (romCursor.getCount() > 0) {
			text.setVisibility(View.GONE);
		} else {
			text
					.setText("No ROMs\n\nControls:\n"
							+ Settings.getKeyName(RomController.key[2]) +" "
							+ Settings.getKeyName(RomController.key[1]) +" "
							+ Settings.getKeyName(RomController.key[3]) +" "
							+ Settings.getKeyName(RomController.key[0]) 
							+ " - Move\n"
							+ Settings.getKeyName(RomController.key[4])
							+ " - A Button\n"
							+ Settings.getKeyName(RomController.key[5])
							+ " - B Button\n"
							+ Settings.getKeyName(RomController.key[7])
							+ " - Start\n"
							+ Settings.getKeyName(RomController.key[6])
							+ " - Select");
			text.setOnClickListener(this);
		}
	}

	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		RomController.isPaused = true;
		if (RomController.dmgcpu != null) {
			//RomController.saveCartRam();
			// RomController.saveState(RomController.state);
			//RomController.saveState("temp");
		}
		return true;
	}

	public void onOptionsMenuClosed(Menu menu) {
		super.onOptionsMenuClosed(menu);
		RomController.isPaused = false;
	}

	@Override
	public void onClick(View v) {
		if (v instanceof TextView) {
			Intent i = new Intent(KiddGBC.this, FileBrowser.class);
			startActivityForResult(i, 1);
			return;
		}

		int id = (Integer) v.getTag();
		String state = "temp";
		int romId = prefs.getInt("prev_rom_id", -1);
		String romName = prefs.getString("prev_rom_name", null);
		String romPath = prefs.getString("prev_rom_path", null);
		if (id >= 0) {
			romCursor.moveToPosition(id);
			romId = romCursor.getInt(romCursor
					.getColumnIndex(RomDatabase.KEY_ROMID));
			state = romCursor.getString(romCursor
					.getColumnIndex(RomDatabase.KEY_STATE_NAME));
			romName = romCursor.getString(romCursor
					.getColumnIndex(RomDatabase.KEY_ROM_NAME));
			romPath = romCursor.getString(romCursor
					.getColumnIndex(RomDatabase.KEY_ROM_PATH));
		}
		startRom(romId, romName, romPath, state);
	}

	private class RomAdapter extends SimpleCursorAdapter {

		public RomAdapter(Context context, int layout, Cursor c, String[] from,
				int[] to) {
			super(context, layout, c, from, to);
		}

		@Override
		public void setViewImage(ImageView v, String value) {
			int stateId = romCursor.getInt(romCursor
					.getColumnIndex(RomDatabase.KEY_SROWID));

			if (IMAGE_WIDTH == 0) {
				v.setVisibility(View.GONE);
				return;
			} else {
				v.setVisibility(View.VISIBLE);
			}
			v.setMinimumWidth(IMAGE_WIDTH + 20);
			v.setMinimumHeight(IMAGE_HEIGHT + 20);
			v.setFocusable(false);
			v.setOnClickListener(KiddGBC.this);
			v.setTag(romCursor.getPosition());
			try {
				Bitmap stateBitmap;
				if (thumbs.containsKey(stateId))
					stateBitmap = thumbs.get(stateId);
				else {
					stateBitmap = Bitmap.createBitmap(unpack(stateId, value),
							160 / 2, 144 / 2, Bitmap.Config.RGB_565);
					stateBitmap = Bitmap.createScaledBitmap(stateBitmap,
							IMAGE_WIDTH, IMAGE_HEIGHT, true);
				}
				v.setImageBitmap(stateBitmap);
				thumbs.put(stateId, stateBitmap);
			} catch (Exception e) {
				v.setImageBitmap(null);
			}
		}
	}

	public static HashMap<Integer, Bitmap> thumbs = new HashMap<Integer, Bitmap>();

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

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position,
			long id) {
		romCursor.moveToPosition(position);
		int romId = romCursor.getInt(romCursor
				.getColumnIndex(RomDatabase.KEY_ROMID));
		String romName = romCursor.getString(romCursor
				.getColumnIndex(RomDatabase.KEY_ROM_NAME));
		String romPath = romCursor.getString(romCursor
				.getColumnIndex(RomDatabase.KEY_ROM_PATH));
		Intent i = new Intent(KiddGBC.this, SaveStates.class);
		i.putExtra("type", "load");
		i.putExtra("romId", romId);
		i.putExtra("romPath", romPath);
		i.putExtra("romName", romName);
		i.putExtra("start", true);
		startActivityForResult(i, 4);
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> arg0, View arg1, final int pos,
			long arg3) {
		
		
		AlertDialog dia = new AlertDialog(this);
		
		dia.setButton(AlertDialog.BUTTON1,"Restart", new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
			
				romCursor.moveToPosition(pos);
				int romId = romCursor.getInt(romCursor
						.getColumnIndex(RomDatabase.KEY_ROMID));
				String romName = romCursor.getString(romCursor
						.getColumnIndex(RomDatabase.KEY_ROM_NAME));
				String romPath = romCursor.getString(romCursor
						.getColumnIndex(RomDatabase.KEY_ROM_PATH));
				
				startRom(romId, romName, romPath, "");
			
			dialog.dismiss();
				
			}
		});
		
		dia.setButton(AlertDialog.BUTTON2 ,"Remove", new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				romCursor.moveToPosition(pos);
				int romId = romCursor.getInt(romCursor
						.getColumnIndex(RomDatabase.KEY_ROMID));
			
				database.deleteRom(romId);
				updateList();
				dialog.dismiss();
			}
		});
		
		dia.setTitle("ROM options");
		dia.setCancelable(true);
		
		dia.show();
		//arg0.getItemAtPosition(pos)
		
		return true;
	}
}