package org.bostwickenator.javagbc;

import android.app.Activity;
import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.widget.TabHost;

public class States extends TabActivity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		TabHost host = getTabHost();

		Intent saveIntent = new Intent(this, SaveStates.class);
		saveIntent.putExtra("type", "save");
		host.addTab(host.newTabSpec("one").setIndicator("Save").setContent(
				saveIntent));
		Intent loadIntent = new Intent(this, SaveStates.class);
		loadIntent.putExtra("type", "load");
		host.addTab(host.newTabSpec("two").setIndicator("Load").setContent(
				loadIntent));
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		setResult(Activity.RESULT_OK, data);
		finish();
	}
}
