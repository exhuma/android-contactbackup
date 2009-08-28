package lu.albert.android.contactbackup;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.Contacts.Photos;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/**
 * Activity to create a JSON backup of you contact list. Not all contact
 * information is stored. So a dump/restore operation is neat certain to lose
 * data. Only use this application if your know what you are doing.
 * 
 * @author exhuma
 */
public class ContactBackup extends Activity {

	private static final int START_ID = Menu.FIRST;
	// private static final int DELETE_ID = Menu.FIRST + 1;
	public static final String FILE_NAME = "contacts.json";
	private static final int DIALOG_ASK_DELETE = 0;
	private static final int DIALOG_CANCELLED = 1;
	private static final int DIALOG_PROGRESS = 2;
	protected static final int DIALOG_FINISHED = 3;
	private TextView mLogText;
	private Button mBackupButton;

	ProgressThread progressThread;
	ProgressDialog progressDialog;

	/**
	 * A handler which deals with updating the progress bar
	 */
	final Handler handler = new Handler() {
		public void handleMessage(Message msg) {
			int position = msg.getData().getInt("position");
			int total = msg.getData().getInt("total");
			progressDialog.setProgress(position);
			progressDialog.setIndeterminate(false);
			progressDialog.setMax(total);
			if (position >= total) {
				dismissDialog(DIALOG_PROGRESS);
				progressThread.setState(ProgressThread.STATE_DONE);
				showDialog(DIALOG_FINISHED);
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		mLogText = (TextView) findViewById(R.id.disclaimer);

		mLogText.setText("--------------------------\n" + "Read this first!\n"
				+ "--------------------------\n"
				+ "If the acronym JSON does not mean anything to you "
				+ "or you don't do software development then this "
				+ "application may not be for you. Please take that into "
				+ "consideration before voting it down!\n"
				+ "--------------------------\n" + "INFO\n"
				+ "--------------------------\n"
				+ "This application creates a file called 'contacts.json'"
				+ "on the external storage device (SDCard). You can start "
				+ "this process by opening the menu and selecting 'Start "
				+ "backup'.\n" + "Use it at your own risk!\n"
				+ "--------------------------\n" + "Why?\n"
				+ "--------------------------\n"
				+ "I am having trouble syncing my contacts with google. As "
				+ "it seems to be working for everyone else, I can only "
				+ "assume that one or more contacts cause problems. That is "
				+ "why I needed a tool to export my contacts. That way I "
				+ "can manually edit them, re-import them onto the phone "
				+ "and then retry syncing.");

		mBackupButton = (Button)findViewById(R.id.backup_button);
		mBackupButton.setOnClickListener( new BackupListener() );
	}

	@Override
	protected Dialog onCreateDialog(int id) {

		Dialog dialog;
		AlertDialog.Builder builder;

		switch (id) {
		case DIALOG_ASK_DELETE:
			builder = new AlertDialog.Builder(this);
			builder.setMessage( "File " + FILE_NAME
							+ " exists. Do you want to overwrite it?")
					.setCancelable(false)
					.setPositiveButton("Yes",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int id) {
								ContactBackup.this.deleteDump();
								showDialog(DIALOG_PROGRESS);
							}
						})
					.setNegativeButton("No",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int id) {
								showDialog(DIALOG_CANCELLED);
								dialog.cancel();
							}
						});
			dialog = builder.create();
			break;

		case DIALOG_CANCELLED:
			builder = new AlertDialog.Builder(this);
			builder.setMessage("Cancelled by user request.")
					.setCancelable(false)
					.setNegativeButton("OK",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
							}
						});
			dialog = builder.create();
			break;

		case DIALOG_FINISHED:
			builder = new AlertDialog.Builder(this);
			builder.setMessage("Operation finished.")
					.setCancelable(false)
					.setNegativeButton("OK",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
							}
						});
			dialog = builder.create();
			break;

		case DIALOG_PROGRESS:
			progressDialog = new ProgressDialog(ContactBackup.this);
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.setMessage("Serializing...");
			progressThread = new ProgressThread(handler);
			progressDialog.setIndeterminate(true);
			progressThread.start();
			dialog = progressDialog;
			break;

		default:
			dialog = null;
		}

		return dialog;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, START_ID, 0, R.string.menu_start);
		// menu.add(0, DELETE_ID, 0, R.string.menu_delete_json);
		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case START_ID:
			startBackup();
			return true;
		}

		return super.onMenuItemSelected(featureId, item);
	}

	/**
	 * Delete the dump file
	 */
	protected void deleteDump() {
		File fp = new File(Environment.getExternalStorageDirectory(), FILE_NAME);
		fp.delete();
	}

	/**
	 * Create a new contacts backup file The file will appear on the external
	 * storage device (SDCard) as FILE_NAME
	 */
	public void startBackup() {

		File file1 = null;
		file1 = new File(Environment.getExternalStorageDirectory(), FILE_NAME);
		if (file1.exists()) {
			showDialog(DIALOG_ASK_DELETE);
		} else {
			showDialog(DIALOG_PROGRESS);
		}

	}

	private class ProgressThread extends Thread {
		Handler mHandler;
		final static int STATE_DONE = 0;
		final static int STATE_RUNNING = 1;
		int mState;
		int total;

		ProgressThread(Handler h) {
			mHandler = h;
		}

		public void run() {
			// Make the query.
			Cursor managedCursor = managedQuery(People.CONTENT_URI, null, // Which
					// columns
					// to
					// return
					null, // Which rows to return (all rows)
					null, // Selection arguments (none)
					// Put the results in ascending order by name
					People._ID + " ASC");

			File file1 = null;
			try {
				file1 = new File(Environment.getExternalStorageDirectory(),
						FILE_NAME);
				file1.createNewFile();
			} catch (IOException e) {
				// TODO User-friendly error message
				mLogText.append(e.getMessage());
				return;
			}
			FileOutputStream file_out = null;
			try {
				file_out = new FileOutputStream(file1);
			} catch (FileNotFoundException e2) {
				// file has just been successfully created. It's there alright!
			}
			BufferedOutputStream buf = new BufferedOutputStream(file_out);
			OutputStreamWriter out_stream = new OutputStreamWriter(buf);

			/*
			 * We don't construct the whole list in memory. Instead we write to
			 * disk after each contact, which should keep memory consumption
			 * low. This means, we cannot create the "root" JSONArray. But
			 * that's nothing more that printing out an opeing "[", some commas,
			 * and a closing "]"
			 */
			try {
				/* The opening "[" */
				out_stream.write("[\n");
			} catch (IOException e) {
				// TODO User-friendly error message
				mLogText.append(e.getMessage());
				return;
			}

			if (managedCursor.moveToFirst()) {

				/*
				 * get the column indexes before the loop to prevent unnecessary
				 * method calls
				 */
				int idColumn = managedCursor.getColumnIndex(People._ID);
				int ringToneColumn = managedCursor
						.getColumnIndex(People.CUSTOM_RINGTONE);
				int displayNameColumn = managedCursor
						.getColumnIndex(People.DISPLAY_NAME);
				int lastTimeContactedColumn = managedCursor
						.getColumnIndex(People.LAST_TIME_CONTACTED);
				int nameColumn = managedCursor.getColumnIndex(People.NAME);
				int notesColumn = managedCursor.getColumnIndex(People.NOTES);
				int phoneticNameColumn = managedCursor
						.getColumnIndex(People.PHONETIC_NAME);
				int photoVersionColumn = managedCursor
						.getColumnIndex(People.PHOTO_VERSION);
				int sendToVoiceMailColumn = managedCursor
						.getColumnIndex(People.SEND_TO_VOICEMAIL);
				int starredColumn = managedCursor
						.getColumnIndex(People.STARRED);
				int timesContactedColumn = managedCursor
						.getColumnIndex(People.TIMES_CONTACTED);

				do {
					int id = managedCursor.getInt(idColumn);
					System.out.println("Dumping "
							+ managedCursor.getString(displayNameColumn));

					JSONObject contact = new JSONObject();
					try {
						contact.put("id", id);
						contact
								.put("name", managedCursor
										.getString(nameColumn));

						contact.put("custom_ring_tone", managedCursor
								.getString(ringToneColumn));
						contact.put("display_name", managedCursor
								.getString(displayNameColumn));
						contact.put("last_time_contacted", managedCursor
								.getString(lastTimeContactedColumn));
						contact.put("notes", managedCursor
								.getString(notesColumn));
						contact.put("phonetic_name", managedCursor
								.getString(phoneticNameColumn));
						contact.put("send_to_voicemail", managedCursor
								.getString(sendToVoiceMailColumn));
						contact.put("starred", managedCursor
								.getString(starredColumn));
						contact.put("times_contacted", managedCursor
								.getString(timesContactedColumn));
						try {
							contact.put("photo_version", managedCursor
									.getString(photoVersionColumn));
						} catch (IllegalStateException e) {
							contact.put("photo_version", null);
							System.err
									.println("Unable to retrieve photo ID for contact #"
											+ id);
						}

						this.appendPhoneNumbers(contact);
						this.appendPhotos(contact);
						out_stream.write(contact.toString(3));
						
						/* Add commas (JSON array grammar) */
						if (managedCursor.getPosition() < managedCursor
								.getCount() - 1) {
							out_stream.write(",\n");
						}

						/*
						 * we flush after each contact. That should keep memory
						 * consumption to a minimum
						 */
						out_stream.flush();
					} catch (JSONException e1) {
						System.err
								.println("Unable to encode JSON for contact #"
										+ id + ":" + e1.getMessage());
					} catch (IOException e) {
						// TODO: User friendly error
						mLogText.append("ERROR: " + e.getMessage() + "\n");
					}

					Message msg = mHandler.obtainMessage();
					Bundle b = new Bundle();
					b.putInt("position", managedCursor.getPosition() + 1);
					b.putInt("total", managedCursor.getCount());
					msg.setData(b);
					mHandler.sendMessage(msg);

				} while (managedCursor.moveToNext());

			}

			try {
				/* Add the closing "]" (JSON array grammar) */
				out_stream.write("]\n");
				out_stream.close();
				buf.close();
				file_out.close();
			} catch (IOException e) {
				// TODO: User friendly error
				System.err.println("ERROR: " + e.getMessage());
			}

		}

		/**
		 * Append photos as Base64 encoded strings to the given contact. This is
		 * an in-place modification of the "contact" object
		 * 
		 * @param contact
		 *            A JSON object representing a contact. It must have at
		 *            least a key named "id" representing the internal
		 *            contact-ID
		 * @throws JSONException
		 *             Thrown when data could not be successfully encoded to
		 *             JSON
		 */
		private void appendPhotos(JSONObject contact) throws JSONException {
			String where = Photos.PERSON_ID + "=" + contact.getInt("id");
			Cursor cursor = managedQuery(Photos.CONTENT_URI, null, where, null,
					null);

			int dataColumn = cursor.getColumnIndex(Photos.DATA);

			JSONArray photos = new JSONArray();

			if (cursor.moveToFirst()) {
				do {
					if (cursor.getBlob(dataColumn) != null) {
						photos.put(Base64.encodeBytes(cursor
								.getBlob(dataColumn)));
					}
				} while (cursor.moveToNext());
			}
			contact.put("photos", photos);
		}

		/**
		 * Append a list of phone numbers to the given contact This is an
		 * in-place modification of the "contact" object
		 * 
		 * @param contact
		 *            A JSON object representing a contact. It must have at
		 *            least a key named "id" representing the internal
		 *            contact-ID
		 * @throws JSONException
		 *             Thrown when data could not be successfully encoded to
		 *             JSON
		 */
		private void appendPhoneNumbers(JSONObject contact)
				throws JSONException {

			String where = Phones.PERSON_ID + "=" + contact.getInt("id");
			Cursor cursor = managedQuery(Phones.CONTENT_URI, null, where, null,
					null);

			int isPrimaryColumn = cursor.getColumnIndex(Phones.ISPRIMARY);
			int labelColumn = cursor.getColumnIndex(Phones.LABEL);
			int numberColumn = cursor.getColumnIndex(Phones.NUMBER);
			int numberKeyColumn = cursor.getColumnIndex(Phones.NUMBER_KEY);
			int typeColumn = cursor.getColumnIndex(Phones.TYPE);

			// TYPE_CUSTOM
			// TYPE_FAX_HOME
			// TYPE_FAX_WORK
			// TYPE_HOME
			// TYPE_MOBILE
			// TYPE_OTHER
			// TYPE_PAGER
			// TYPE_WORK

			JSONArray phonenumbers = new JSONArray();
			JSONObject number = new JSONObject();

			if (cursor.moveToFirst()) {
				do {
					number.put("is_primary",
							(cursor.getInt(isPrimaryColumn) != 0));
					number.put("label", cursor.getString(labelColumn));
					number.put("number", cursor.getString(numberColumn));
					number.put("number_key", cursor.getString(numberKeyColumn));
					number.put("type", cursor.getString(typeColumn));
					phonenumbers.put(number);
				} while (cursor.moveToNext());
			}
			contact.put("phonenumbers", phonenumbers);

		}

		/*
		 * sets the current state for the thread, used to stop the thread
		 */
		public void setState(int state) {
			mState = state;
		}
	}
	
	private class BackupListener implements OnClickListener{

		@Override
		public void onClick(View v) {
			File file1 = null;
			file1 = new File(Environment.getExternalStorageDirectory(), FILE_NAME);
			if (file1.exists()) {
				showDialog(DIALOG_ASK_DELETE);
			} else {
				showDialog(DIALOG_PROGRESS);
			}
		}
		
	}

}