package lu.albert.android.contactbackup;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import lu.albert.android.contactbackup.schema.ContactColumns;
import lu.albert.android.contactbackup.schema.ContactColumns.OrganizationColumns;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Contacts.ContactMethods;
import android.provider.Contacts.Organizations;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.Contacts.Photos;
import android.util.Log;

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
		
		BufferedOutputStream stream_buffer = new BufferedOutputStream(file_stream, 1000*managedCursor.getCount());
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
//			int photoVersionColumn = managedCursor
//					.getColumnIndex(People.PHOTO_VERSION);
			int sendToVoiceMailColumn = managedCursor
					.getColumnIndex(People.SEND_TO_VOICEMAIL);
			int starredColumn = managedCursor
					.getColumnIndex(People.STARRED);
			int timesContactedColumn = managedCursor
					.getColumnIndex(People.TIMES_CONTACTED);

			do {
				int id = managedCursor.getInt(idColumn);
				Log.i(ContactBackup.TAG, String.format(
						"Dumping %s",
						managedCursor.getString(displayNameColumn)));

				JSONObject contact = new JSONObject();
				try {
					contact.put(ContactColumns.ID, id);
					contact.put(ContactColumns.NAME,
							managedCursor.getString(nameColumn));
					contact.put(ContactColumns.CUSTOM_RING_TONE, managedCursor
							.getString(ringToneColumn));
					contact.put(ContactColumns.DISPLAY_NAME, managedCursor
							.getString(displayNameColumn));
					contact.put(ContactColumns.LAST_TIME_CONTACTED, managedCursor
							.getString(lastTimeContactedColumn));
					contact.put(ContactColumns.NOTES, managedCursor
							.getString(notesColumn));
					contact.put(ContactColumns.PHONETIC_NAME, managedCursor
							.getString(phoneticNameColumn));
					contact.put(ContactColumns.SEND_TO_VOICEMAIL, managedCursor
							.getString(sendToVoiceMailColumn));
					contact.put(ContactColumns.STARRED, managedCursor
							.getString(starredColumn));
					contact.put(ContactColumns.TIMES_CONTACTED, managedCursor
							.getString(timesContactedColumn));
//       Seems buggy...
//					try {
//						contact.put(ContactColumns.PHOTO_VERSION, managedCursor
//								.getString(photoVersionColumn));
//					} catch (IllegalStateException e) {
//						contact.put(ContactColumns.PHOTO_VERSION, null);
//						Log.e(ContactBackup.TAG, 
//								"Unable to retrieve photo_version for contact #"
//										+ id);
//					}

					this.appendContactMethods(contact);
					this.appendPhotos(contact);
					this.appendPhoneNumbers(contact);
					this.appendOrganizations(contact);
					
					writer.write(contact.toString(3));
					
					/* Add commas (JSON array grammar) */
					if (managedCursor.getPosition() < managedCursor
							.getCount() - 1) {
						writer.write(",\n");
					}

					/*
					 * we flush after each contact. That should keep memory
					 * consumption to a minimum.
					 */
					writer.flush();
				} catch (JSONException e1) {
					Log.e(ContactBackup.TAG, String.format(
							"Unable to encode JSON for contact #%d (%s)", id, e1.getMessage()));
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
			Log.e(ContactBackup.TAG, "ERROR: " + e.getMessage());
		}

	}

	/**
	 * sets the current state for the thread, used to stop the thread
	 * 
	 * @param state The new state
	 */
	public void setState(int state) {
		mState = state;
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
		String where = Photos.PERSON_ID + "=" + contact.getInt( ContactColumns.ID );
		Cursor cursor = mParent.managedQuery(Photos.CONTENT_URI, null, where, null,
				null);

		JSONArray photos = new JSONArray();

		if ( cursor != null ) {
			
			int dataColumn = cursor.getColumnIndex(Photos.DATA);
	
			if (cursor.moveToFirst()) {
				do {
					if (cursor.getBlob(dataColumn) != null) {
						photos.put(Base64.encodeBytes(cursor
								.getBlob(dataColumn)));
					}
				} while (cursor.moveToNext());
			}
		}
		
		contact.put( ContactColumns.PHOTOS, photos);
		
		cursor.close();
		
	}
	
	/**
	 * Append all non-phone contact methods to the contact
	 * 
	 * @param contact
	 *            A JSON object representing a contact. It must have at
	 *            least a key named "id" representing the internal
	 *            contact-ID
	 * @throws JSONException
	 *             Thrown when data could not be successfully encoded to
	 *             JSON
	 */
	private void appendContactMethods( JSONObject contact ) throws JSONException{
		
		String where = ContactMethods.PERSON_ID + "=" + contact.getInt( ContactColumns.ID );
		Cursor cursor = mParent.managedQuery(ContactMethods.CONTENT_URI, null, where, null,
				null);
		
		JSONArray contactMethods = new JSONArray();

		if ( cursor != null ) {
		
			int auxDataColumn = cursor.getColumnIndex(ContactMethods.AUX_DATA);
			int dataColumn = cursor.getColumnIndex(ContactMethods.DATA);
			int isPrimaryColumn = cursor.getColumnIndex(ContactMethods.ISPRIMARY);
			int kindColumn = cursor.getColumnIndex(ContactMethods.KIND);
			int labelColumn = cursor.getColumnIndex(ContactMethods.LABEL);
			int typeColumn = cursor.getColumnIndex(ContactMethods.TYPE);
			
	
			if (cursor.moveToFirst()) {
				do {
					JSONObject method = new JSONObject();
					method.put(ContactColumns.ContactMethodColumns.IS_PRIMARY,
							(cursor.getInt(isPrimaryColumn) != 0));
					method.put(ContactColumns.ContactMethodColumns.LABEL,
							cursor.getString(labelColumn));
					method.put(ContactColumns.ContactMethodColumns.TYPE,
							cursor.getString(typeColumn));
					method.put(ContactColumns.ContactMethodColumns.AUX_DATA,
							cursor.getString(auxDataColumn));
					method.put(ContactColumns.ContactMethodColumns.DATA,
							cursor.getString(dataColumn));
					method.put(ContactColumns.ContactMethodColumns.KIND,
							cursor.getString(kindColumn));
					contactMethods.put(method);
				} while (cursor.moveToNext());
			}
		}
		
		contact.put(ContactColumns.CONTACT_METHODS, contactMethods);
		
		cursor.close();

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

		String where = Phones.PERSON_ID + "=" + contact.getInt( ContactColumns.ID );
		Cursor cursor = mParent.managedQuery(Phones.CONTENT_URI, null, where, null,
				null);
		JSONArray phonenumbers = new JSONArray();

		if ( cursor != null ) {

			int isPrimaryColumn = cursor.getColumnIndex(Phones.ISPRIMARY);
			int labelColumn = cursor.getColumnIndex(Phones.LABEL);
			int numberColumn = cursor.getColumnIndex(Phones.NUMBER);
			int numberKeyColumn = cursor.getColumnIndex(Phones.NUMBER_KEY);
			int typeColumn = cursor.getColumnIndex(Phones.TYPE);
	
			if (cursor.moveToFirst()) {
				do {
					JSONObject number = new JSONObject();
					number.put(ContactColumns.PhoneColumns.IS_PRIMARY,
							(cursor.getInt(isPrimaryColumn) != 0));
					number.put(ContactColumns.PhoneColumns.LABEL,
							cursor.getString(labelColumn));
					number.put(ContactColumns.PhoneColumns.NUMBER,
							cursor.getString(numberColumn));
					number.put(ContactColumns.PhoneColumns.NUMBER_KEY,
							cursor.getString(numberKeyColumn));
					number.put(ContactColumns.PhoneColumns.TYPE,
							cursor.getString(typeColumn));
					phonenumbers.put(number);
				} while (cursor.moveToNext());
			}
		}
		contact.put(ContactColumns.PHONE_NUMBERS, phonenumbers);

		cursor.close();

	}

	/**
	 * Append a list of organizations to the given contact This is an
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
	private void appendOrganizations(JSONObject contact)
		throws JSONException {

		String where = Organizations.PERSON_ID + "=" + contact.getInt( ContactColumns.ID );
		Cursor cursor = mParent.managedQuery(Organizations.CONTENT_URI, null, where, null,
				null);

		JSONArray organizations = new JSONArray();
		
		if ( cursor != null ) {
			int isPrimaryColumn = cursor.getColumnIndex(Organizations.ISPRIMARY);
			int labelColumn = cursor.getColumnIndex(Organizations.LABEL);
			int typeColumn = cursor.getColumnIndex(Organizations.TYPE);
			int companyColumn = cursor.getColumnIndex(Organizations.COMPANY);
			int titleColumn = cursor.getColumnIndex(Organizations.TITLE);
	
			if (cursor.moveToFirst()) {
				do {
					JSONObject org = new JSONObject();
					org.put(OrganizationColumns.IS_PRIMARY,
							(cursor.getInt(isPrimaryColumn) != 0));
					org.put(OrganizationColumns.LABEL,
							cursor.getString(labelColumn));
					org.put(OrganizationColumns.TITLE,
							cursor.getString(titleColumn));
					org.put(OrganizationColumns.COMPANY,
							cursor.getString(companyColumn));
					org.put(OrganizationColumns.TYPE,
							cursor.getString(typeColumn));
					organizations.put(org);
				} while (cursor.moveToNext());
			}
		}
		
		contact.put(ContactColumns.ORGANIZATIONS, organizations);

		cursor.close();

	}
	
}

