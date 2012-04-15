package org.bostwickenator.javagbc;

import java.io.File;
import java.io.FilenameFilter;

import org.bostwickenator.javagbc.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Toast;

public class SaveStates extends Activity implements
		AdapterView.OnItemClickListener, View.OnClickListener {

	int romId;
	String romFile, romPath, romName;
	String save;

	Cursor stateCursor;
	ListView stateListView;
	StateAdapter stateAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.savestates);
		save = this.getIntent().getStringExtra("type");

		romId = this.getIntent().getIntExtra("romId", -1);
		romName = this.getIntent().getStringExtra("romName");
		romPath = this.getIntent().getStringExtra("romPath");

		this.setTitle(romName);

		try{
		updateListOfStates(romPath);
		}catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(this, "Cannot access files", Toast.LENGTH_LONG).show();
			finish();
			return;
		}
		stateCursor = KiddGBC.database.fetchStates(romId);

		if (stateCursor.getCount() == 0
				&& this.getIntent().getBooleanExtra("start", false))
			stateSelected(-1);

		String[] from = new String[] { RomDatabase.KEY_STATE_NAME,
				RomDatabase.KEY_STATE_IMAGE };
		int[] to = new int[] { R.id.romname, R.id.romimage };
		stateAdapter = new StateAdapter(this, R.layout.romrow, stateCursor,
				from, to);

		stateListView = (ListView) this.findViewById(R.id.list);
		stateListView.setAdapter(stateAdapter);
		stateListView.setOnItemClickListener(this);
		this.registerForContextMenu(stateListView);

		if (!save.equals("save"))
			this.findViewById(R.id.savearea).setVisibility(View.GONE);
		else {
			Button saveButton = (Button) this.findViewById(R.id.saveButton);
			saveButton.setOnClickListener(this);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item
				.getMenuInfo();
		stateCursor.moveToPosition(menuInfo.position);
		int rowId = stateCursor.getInt(stateCursor
				.getColumnIndex(RomDatabase.KEY_ROWID));

		KiddGBC.database.deleteState(rowId);
		String statePath = Utils.stripExtention(romPath)
				+ "_"
				+ stateCursor.getString(stateCursor
						.getColumnIndex(RomDatabase.KEY_STATE_NAME)) + ".stt";
		System.out.println(statePath);
		new File(statePath).delete();
		updateList();
		return super.onContextItemSelected(item);
	}

	private void updateList() {
		stateCursor = KiddGBC.database.fetchStates(romId);
		stateAdapter.changeCursor(stateCursor);
		stateAdapter.notifyDataSetChanged();
		stateListView.invalidate();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		menu.add(0, 0, 0, "Remove Save");
		super.onCreateContextMenu(menu, v, menuInfo);
	}

	@Override
	public void onItemClick(AdapterView l, View v, int position, long id) {
		stateSelected(position);
	}

	private void stateSelected(int position) {
		RomController.stateChanged = save;

		Intent i = new Intent();
		if (position == -1) {
			i.putExtra("state", "temp");
		} else {
			stateCursor.moveToPosition(position);
			String state = stateCursor.getString(stateCursor
					.getColumnIndex(RomDatabase.KEY_STATE_NAME));
			i.putExtra("state", state);
			if (save.equals("save")) {
				KiddGBC.database.addState(RomController.romId, state);
				RomController.saveState(state);
			}
			RomController.stateChanged = "save";
		}
		i.putExtra("save", save);
		i.putExtra("romId", romId);
		i.putExtra("romName", romPath);
		i.putExtra("romPath", romPath);
		this.setResult(Activity.RESULT_OK, i);

		finish();
	}

	public void updateListOfStates(String romPath) {
		File file = new File(romPath);
		romFile = Utils.stripExtention(file.getName());
		romPath = file.getAbsolutePath();
		String[] states = file.getParentFile().list(new StateFilter());

		for (int i = 0; i < states.length; i++) {
			states[i] = states[i].substring(romFile.length() + 1,
					states[i].length() - 4);
			KiddGBC.database.addState(romId, states[i]);
		}
	}

	class StateFilter implements FilenameFilter {
		@Override
		public boolean accept(File dir, String filename) {
			if (filename.startsWith(romFile) && filename.endsWith(".stt")
					&& !filename.endsWith("_temp.stt"))
				return true;
			return false;
		}
	}

	@Override
	public void onClick(View v) {
		if (v instanceof ImageView) {
			stateSelected((Integer) v.getTag());
			return;
		}
		EditText text = (EditText) this.findViewById(R.id.savefile);
		String state = text.getText().toString();
		if (save.equals("save")) {
			KiddGBC.database.addState(RomController.romId, state);
			RomController.saveState(state);
		}
		RomController.stateChanged = "save";
		finish();
	}

	private class StateAdapter extends SimpleCursorAdapter {

		public StateAdapter(Context context, int layout, Cursor c,
				String[] from, int[] to) {
			super(context, layout, c, from, to);
		}

		@Override
		public void setViewImage(ImageView v, String value) {

			v.setOnClickListener(SaveStates.this);
			v.setMinimumWidth(KiddGBC.IMAGE_WIDTH + 20);
			v.setMinimumHeight(KiddGBC.IMAGE_HEIGHT + 20);
			v.setTag(stateCursor.getPosition());
			v.setFocusable(false);
			try {
				int id = stateCursor.getInt(stateCursor
						.getColumnIndex(RomDatabase.KEY_ROWID));
				Bitmap stateBitmap;
				if (KiddGBC.thumbs.containsKey(id))
					stateBitmap = KiddGBC.thumbs.get(id);
				else
					stateBitmap = Bitmap
							.createBitmap(
									KiddGBC.unpack(
											id,
											stateCursor.getString(stateCursor
													.getColumnIndex(RomDatabase.KEY_STATE_IMAGE))),
									160 / 2, 144 / 2, Bitmap.Config.RGB_565);
				stateBitmap = Bitmap.createScaledBitmap(stateBitmap,
						KiddGBC.IMAGE_WIDTH, KiddGBC.IMAGE_HEIGHT, true);
				v.setImageBitmap(stateBitmap);
				KiddGBC.thumbs.put(id, stateBitmap);
			} catch (Exception e) {
				v.setImageBitmap(null);
			}
		}
	}

}
