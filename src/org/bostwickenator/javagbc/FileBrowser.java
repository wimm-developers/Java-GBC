package org.bostwickenator.javagbc;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.bostwickenator.javagbc.R;

import com.wimm.framework.app.Dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

public class FileBrowser extends ListActivity {

	private enum DISPLAYMODE {
		ABSOLUTE, RELATIVE;
	}

	private final DISPLAYMODE displayMode = DISPLAYMODE.RELATIVE;
	private List<IconifiedText> directoryEntries = new ArrayList<IconifiedText>();
	private File currentDirectory = new File("/");

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		// this.set
		browseToRoot();
	}

	/**
	 * This function browses to the root-directory of the file-system.
	 */
	private void browseToRoot() {
		if(!browseTo(new File("/sdcard/")))
			finish();
	}

	/**
	 * This function browses up one level according to the field:
	 * currentDirectory
	 */
	private void upOneLevel() {
		if (this.currentDirectory.getParent() != null)
			this.browseTo(this.currentDirectory.getParentFile());
		else{
			finish();
		}
	}

	private boolean browseTo(final File aDirectory) {
		// On relative we display the full path in the title.
		if (this.displayMode == DISPLAYMODE.RELATIVE)
			this.setTitle(aDirectory.getAbsolutePath() + " :: "
					+ getString(R.string.app_name));
		if (aDirectory.isDirectory() ) {
			
			
			if( aDirectory.listFiles() ==null){
				Toast.makeText(getBaseContext(), "No files", Toast.LENGTH_LONG).show();
				return false;
			}
			this.currentDirectory = aDirectory;
			fill(aDirectory.listFiles());
		} else {
			
			com.wimm.framework.app.AlertDialog dia = new com.wimm.framework.app.AlertDialog(this);// Dialog(this);
			
			dia.setMessage("Do you want to add this Rom?\n" + aDirectory.getName());
			dia.setTitle("Add ROM");
			dia.setButton(AlertDialog.BUTTON_POSITIVE, "YES", new OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					FileBrowser.this.openFile(aDirectory);
				}
			});
			dia.setButton(AlertDialog.BUTTON_NEGATIVE, "NO", new OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			});
			dia.show();
			
			
		}
		return true;
	}

	private void openFile(File aFile) {
		
		if (aFile.getName().toLowerCase().indexOf("zip") > -1) {
			if(Utils.findRomInZip(aFile.getAbsolutePath())==null){
				Toast.makeText(getBaseContext(), "No ROM in ZIP", Toast.LENGTH_LONG).show();
				return;
			}
		}
		
		
		
		Intent i = new Intent();
		i.putExtra("name", aFile.getName());
		i.putExtra("path",  aFile.getAbsolutePath());
		setResult(Activity.RESULT_OK, i);
		finish();
		
		//displayEnterName(aFile.getName(), aFile.getAbsolutePath());
	}
/*
	private void displayEnterName(String fileName, final String path) {

		final EditText editText = new EditText(this);
		editText.setText(fileName);
		new AlertDialog.Builder(this).setTitle("Enter Name of Rom").setView(
				editText).setPositiveButton("Ok",
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						Intent i = new Intent();
						i.putExtra("name", editText.getText().toString());
						i.putExtra("path", path);
						setResult(Activity.RESULT_OK, i);
						finish();
					}
				}).show();
	}*/

	private void fill(File[] filesArray) {
		
		if(filesArray==null)
			return;
		
		this.directoryEntries.clear();

		// Add the "." == "current directory"
		this.directoryEntries.add(new IconifiedText("..", getResources()
				.getDrawable(R.drawable.uponelevel)));
		// and the ".." == 'Up one level'
		// if (this.currentDirectory.getParent() != null)
		// this.directoryEntries.add(new IconifiedText("Current Dir",
		// getResources().getDrawable(R.drawable.uponelevel)));
		List<File> files = Arrays.asList(filesArray);
		
		
		Collections.sort(files, new Comparator<File>() {
			@Override
			public int compare(File lhs, File rhs) {
				
				return lhs.getName().compareToIgnoreCase(rhs.getName());
			}
		});

		
		Drawable currentIcon = null;
		for (File currentFile : files) {
			if (currentFile.isDirectory()) {
				currentIcon = getResources().getDrawable(R.drawable.folder);
			} else {
				String fileName = currentFile.getName();
				String lower = fileName.toLowerCase();
				if ( !(Utils.isRomFile(fileName) || lower.endsWith(".jar") || lower.endsWith(".zip") || lower.endsWith(".gz")))
					continue;
				/*
				 * Determine the Icon to be used, depending on the FileEndings
				 * defined in: res/values/fileendings.xml.
				 */
				// if (checkEndsWithInStringArray(fileName, getResources()
				// .getStringArray(R.array.fileEndingImage))) {
				// currentIcon = getResources().getDrawable(R.drawable.image);
				// } else if (checkEndsWithInStringArray(fileName,
				// getResources()
				// .getStringArray(R.array.fileEndingWebText))) {
				// currentIcon = getResources()
				// .getDrawable(R.drawable.webtext);
				// } else if (checkEndsWithInStringArray(fileName,
				// getResources()
				// .getStringArray(R.array.fileEndingPackage))) {
				// currentIcon = getResources().getDrawable(R.drawable.packed);
				// } else if (checkEndsWithInStringArray(fileName,
				// getResources()
				// .getStringArray(R.array.fileEndingAudio))) {
				// currentIcon = getResources().getDrawable(R.drawable.audio);
				// } else {
				currentIcon = getResources().getDrawable(R.drawable.text);
				// }
			}
			switch (this.displayMode) {
			case ABSOLUTE:
				/* On absolute Mode, we show the full path */
				this.directoryEntries.add(new IconifiedText(currentFile
						.getPath(), currentIcon));
				break;
			case RELATIVE:
				/*
				 * On relative Mode, we have to cut the current-path at the
				 * beginning
				 */
				int currentPathStringLenght = this.currentDirectory
						.getAbsolutePath().length();
				this.directoryEntries.add(new IconifiedText(currentFile
						.getAbsolutePath().substring(currentPathStringLenght),
						currentIcon));

				break;
			}
		}
		// Collections.sort(this.directoryEntries);

		IconifiedTextListAdapter itla = new IconifiedTextListAdapter(this);
		itla.setListItems(this.directoryEntries);
		this.setListAdapter(itla);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);

		String selectedFileString = this.directoryEntries.get(position)
				.getText();
		if (selectedFileString.equals("..")) {
			this.upOneLevel();
		} else {
			File clickedFile = null;
			switch (this.displayMode) {
			case RELATIVE:
				clickedFile = new File(this.currentDirectory.getAbsolutePath()
						+ this.directoryEntries.get(position).getText());
				break;
			case ABSOLUTE:
				clickedFile = new File(this.directoryEntries.get(position)
						.getText());
				break;
			}
			if (clickedFile != null)
				this.browseTo(clickedFile);
		}
	}

	/**
	 * Checks whether checkItsEnd ends with one of the Strings from fileEndings
	 */
	private boolean checkEndsWithInStringArray(String checkItsEnd,
			String[] fileEndings) {
		for (String aEnd : fileEndings) {
			if (checkItsEnd.endsWith(aEnd))
				return true;
		}
		return false;
	}
}