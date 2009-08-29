package lu.albert.android.contactbackup;

import java.io.File;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/**
 * Activity to create a plain-text backup of you contact list. Only the most
 * basic contact information is stored. So a dump/restore operation is
 * guaranteed to lose data. Only use this application if your know what you
 * are doing.
 * 
 * @author Michel Albert <michel@albert.lu>
 */
public class ContactBackup extends Activity {

	/** The filename that will be stored on disk */
	public static final String FILE_NAME = "contacts.json";
	private static final int DIALOG_CONFIRM_OVERWRITE = 0;
	private static final int DIALOG_CANCELLED = 1;
	private static final int DIALOG_BACKUP_PROGRESS = 2;
	protected static final int DIALOG_FINISHED = 3;
	private static final int DIALOG_RESTORE_PROGRESS = 4;
	private static final int DIALOG_ERROR = 5;
	protected static final int RESTORE_MSG_PROGRESS = 0;
	protected static final int RESTORE_MSG_INFO = 1;
	private TextView mLogText;
	private Button mBackupButton;
	private Button mRestoreButton;
	private BackupThread progressThread;
	private RestoreThread restoreThread;
	private ProgressDialog progressDialog;
	private AlertDialog mErrorDialog;

	/**
	 * A handler which deals with updating the progress bar
	 * while creating a backup file
	 */
	final Handler dumpHandler = new Handler() {
		public void handleMessage(Message msg) {
			int position = msg.getData().getInt("position");
			int total = msg.getData().getInt("total");
			progressDialog.setProgress(position);
			progressDialog.setIndeterminate(false);
			progressDialog.setMax(total);
			if (position >= total) {
				dismissDialog(DIALOG_BACKUP_PROGRESS);
				progressThread.setState(BackupThread.STATE_DONE);
				showDialog(DIALOG_FINISHED);
			}
		}
	};
	
	/**
	 * A handler which deals with updating the progress bar
	 * while restoring contacts
	 */
	final Handler restore_handler = new Handler() {
		public void handleMessage(Message msg) {
			
			switch ( msg.what ){
			case RESTORE_MSG_PROGRESS:
				long position = msg.getData().getLong("position");
				long total = msg.getData().getLong("total");
				progressDialog.setProgress((int)position);
				progressDialog.setIndeterminate(false);
				progressDialog.setMax((int)total);
				if (position >= total) {
					dismissDialog(DIALOG_RESTORE_PROGRESS);
					restoreThread.setState(BackupThread.STATE_DONE);
					showDialog(DIALOG_FINISHED);
				}
				break;
			case RESTORE_MSG_INFO:
				String name = msg.getData().getString("name");
				progressDialog.setMessage( "Restored " + name );
				break;
			default:
				// do nothing
				break;
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		mLogText = (TextView) findViewById(R.id.disclaimer);

		// TODO refactor this text out of the code and make it look nicer
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
		mRestoreButton = (Button)findViewById(R.id.restore_button);
		mRestoreButton.setOnClickListener( new RestoreListener() );
		
		AlertDialog.Builder builder = new AlertDialog.Builder(ContactBackup.this);
		builder.setTitle(getString(R.string.error))
				.setMessage(getString(R.string.unspecified_error))
				.setCancelable(false)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setNegativeButton(getString(android.R.string.ok),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.cancel();
						}
					});
		mErrorDialog = builder.create();

		
	}

	@Override
	protected Dialog onCreateDialog(int id) {

		Dialog dialog;
		AlertDialog.Builder builder;

		switch (id) {
		case DIALOG_CONFIRM_OVERWRITE:
			/*
			 * Create a dialog which asks the user if the existing file should
			 * be overwritten (it will be deleted before the new one is 
			 * created)
			 * 
			 * TODO: create a temporary file and move it to the destination file on success. To prevent data corruption
			 */
			builder = new AlertDialog.Builder(this);
			
			builder.setMessage(
					String.format(getString(R.string.file_exists), FILE_NAME))
					.setCancelable(false)
					.setPositiveButton(getString(android.R.string.yes),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int id) {
								ContactBackup.this.deleteDump();
								showDialog(DIALOG_BACKUP_PROGRESS);
							}
						})
					// XXX: This string resolves to "cancel". I'm hoping it will be fixed in a future SDK release, so I'll leave that here.
					.setNegativeButton(getString(android.R.string.no),
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
			/*
			 * Tell the user that the operation was cancelled
			 */
			builder = new AlertDialog.Builder(this);
			builder.setMessage(getString(R.string.cancelled_by_user_request))
					.setCancelable(false)
					.setNegativeButton(getString(android.R.string.ok),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
							}
						});
			dialog = builder.create();
			break;

		case DIALOG_FINISHED:
			/*
			 * Tell the user that the operation finished as expected
			 */
			builder = new AlertDialog.Builder(ContactBackup.this);
			builder.setMessage(getString(R.string.operation_finished))
					.setCancelable(false)
					.setNegativeButton(getString(android.R.string.ok),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
							}
						});
			dialog = builder.create();
			break;

		case DIALOG_BACKUP_PROGRESS:
			/*
			 * Display the backup progress
			 */
			progressDialog = new ProgressDialog(ContactBackup.this);
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.setMessage(getString(R.string.serializing));
			progressThread = new BackupThread(dumpHandler, this);
			progressDialog.setIndeterminate(true);
			progressThread.start();
			dialog = progressDialog;
			break;

		case DIALOG_RESTORE_PROGRESS:
			/*
			 * Display the restoration progress
			 */
			progressDialog = new ProgressDialog(ContactBackup.this);
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.setMessage(getString(R.string.restoring));
			restoreThread = new RestoreThread(restore_handler, this);
			progressDialog.setIndeterminate(true);
			restoreThread.start();
			dialog = progressDialog;
			break;
			
		case DIALOG_ERROR:
			/*
			 * Display a generic error dialog.
			 */
			dialog = mErrorDialog;
			break;

		default:
			/*
			 * If an invalid dialog was specified, do nothing 
			 */
			dialog = null;
		}

		return dialog;
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
			showDialog(DIALOG_CONFIRM_OVERWRITE);
		} else {
			showDialog(DIALOG_BACKUP_PROGRESS);
		}

	}

	/**
	 * Listens to clicks on the "start backup" button
	 * 
	 * @author Michel Albert <michel@albert.lu>
	 */
	private class BackupListener implements OnClickListener{

		@Override
		public void onClick(View v) {
			File file1 = null;
			file1 = new File(Environment.getExternalStorageDirectory(), FILE_NAME);
			if (file1.exists()) {
				showDialog(DIALOG_CONFIRM_OVERWRITE);
			} else {
				showDialog(DIALOG_BACKUP_PROGRESS);
			}
		}
		
	}
	
	/**
	 * Listens to clicks on the "restore" button
	 * 
	 * @author Michel Albert <michel@albert.lu>
	 */
	private class RestoreListener implements OnClickListener{

		@Override
		public void onClick(View v) {
			File file1 = null;
			file1 = new File(Environment.getExternalStorageDirectory(), FILE_NAME);
			if (file1.exists()) {
				showDialog(DIALOG_RESTORE_PROGRESS);
			}
		}
		
	}

}