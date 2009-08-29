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

import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.Contacts.Photos;

/**
 * The thread which stores the contacts into a text-file on-disk
 * 
 * @author Michel Albert <michel.albert@statec.etat.lu>
 */
public class BackupThread extends Thread {
	
	Handler mHandler;
	final static int STATE_DONE = 0;
	final static int STATE_RUNNING = 1;
	int mState;
	int total;
	ContactBackup mParent;

	/**
	 * Constructor
	 * 
	 * @param dialog_handler A handler which is used to communicate with the progress dialog
	 * @param parent The main Activity (UI) class
	 */
	BackupThread(Handler dialog_handler, ContactBackup parent) {
		mHandler = dialog_handler;
		mParent = parent;
	}

	public void run() {
		Cursor managedCursor = mParent.managedQuery(People.CONTENT_URI, null,
				null,
				null,
				People._ID + " ASC");
		
		File backup_file = null;
		try {
			backup_file = new File(Environment.getExternalStorageDirectory(),
					ContactBackup.FILE_NAME);
			backup_file.createNewFile();
		} catch (IOException e) {
			// TODO: user-friendly error message
			return;
		}
		FileOutputStream file_stream = null;
		try {
			file_stream = new FileOutputStream(backup_file);
		} catch (FileNotFoundException e2) {
			// file has just been successfully created. It's there alright!
		}
		BufferedOutputStream stream_buffer = new BufferedOutputStream(file_stream);
		OutputStreamWriter writer = new OutputStreamWriter(stream_buffer);

		/*
		 * We don't construct the whole list in memory. Instead we write to
		 * disk after each contact, which should keep memory consumption
		 * low. This means, we cannot create the "root" JSONArray. But
		 * that's nothing more that printing out an opeing "[", some commas,
		 * and a closing "]"
		 */
		try {
			/* The opening "[" */
			writer.write("[\n");
		} catch (IOException e) {
			// TODO User-friendly error message
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
					writer.write(contact.toString(3));
					
					/* Add commas (JSON array grammar) */
					if (managedCursor.getPosition() < managedCursor
							.getCount() - 1) {
						writer.write(",\n");
					}

					/*
					 * we flush after each contact. That should keep memory
					 * consumption to a minimum
					 */
					writer.flush();
				} catch (JSONException e1) {
					System.err
							.println("Unable to encode JSON for contact #"
									+ id + ":" + e1.getMessage());
				} catch (IOException e) {
					// TODO: User friendly error
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
			writer.write("]\n");
			writer.close();
			stream_buffer.close();
			file_stream.close();
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
		Cursor cursor = mParent.managedQuery(Photos.CONTENT_URI, null, where, null,
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
		Cursor cursor = mParent.managedQuery(Phones.CONTENT_URI, null, where, null,
				null);

		int isPrimaryColumn = cursor.getColumnIndex(Phones.ISPRIMARY);
		int labelColumn = cursor.getColumnIndex(Phones.LABEL);
		int numberColumn = cursor.getColumnIndex(Phones.NUMBER);
		int numberKeyColumn = cursor.getColumnIndex(Phones.NUMBER_KEY);
		int typeColumn = cursor.getColumnIndex(Phones.TYPE);

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

	/**
	 * sets the current state for the thread, used to stop the thread
	 * 
	 * @param state The new state
	 */
	public void setState(int state) {
		mState = state;
	}
}

