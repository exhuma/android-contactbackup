package lu.albert.android.contactbackup.schema;

/**
 * This class defines the key names as used in the JSON dump. This gives us
 * greater flexibility for the future and avoids typos in the code
 * 
 * @author Michel Albert <michel@albert.lu>
 */
public class ContactColumns {

	/** 
	 * The ID as used internally by android.
	 * @see android.provider.Contacts.PeopleColumns._ID
	 */
	public static final String ID = "id";
	
	/** @see android.provider.Contacts.PeopleColumns.NAME */
	public static final String NAME = "name";
	
	/** @see android.provider.Contacts.PeopleColumns.CUSTOM_RINGTONE */
	public static final String CUSTOM_RING_TONE = "custom_ring_tone";
	
	/** @see android.provider.Contacts.PeopleColumns.DISPLAY_NAME */
	public static final String DISPLAY_NAME = "display_name";
	
	/** @see android.provider.Contacts.PeopleColumns.LAST_TIME_CONTACTED */
	public static final String LAST_TIME_CONTACTED = "last_time_contacted";
	
	/** @see android.provider.Contacts.PeopleColumns.NOTES */
	public static final String NOTES = "notes";
	
	/** @see android.provider.Contacts.PeopleColumns.PHONETIC_NAME */
	public static final String PHONETIC_NAME = "phonetic_name";
	
	/** @see android.provider.Contacts.PeopleColumns.SEND_TO_VOICEMAIL */
	public static final String SEND_TO_VOICEMAIL = "send_to_voicemail";
	
	/** @see android.provider.Contacts.PeopleColumns.STARRED */
	public static final String STARRED = "starred";
	
	/** @see android.provider.Contacts.PeopleColumns.TIMES_CONTACTED */
	public static final String TIMES_CONTACTED = "times_contacted";
	
	/** @see android.provider.Contacts.PeopleColumns.PHOTO_VERSION */
	public static final String PHOTO_VERSION = "photo_version";
	
	/**
	 * Maps to an array containing photos associated with that person
	 */
	public static final String PHOTOS = "photos";
	
	/** @see ContactMethodColumns */
	public static final String CONTACT_METHODS = "contact_methods";
	
	/** @see PhoneColumns */
	public static final String PHONE_NUMBERS = "phone_numbers";
	
	/** @see OrganizationColumns */
	public static final String ORGANIZATIONS = "organizations";
	
	/**
	 * A sub directory containing the contact methods of this contact
	 * 
	 * @author Michel Albert <michel@albert.lu>
	 */
	public interface ContactMethodColumns {
		
		/** @see android.provider.Contacts.ContactMethodsColumns.IS_PRIMARY */
		public static final String IS_PRIMARY = "is_primary";
		
		/** @see android.provider.Contacts.ContactMethodsColumns.LABEL */
		public static final String LABEL = "label";
		
		/** @see android.provider.Contacts.ContactMethodsColumns.TYPE */
		public static final String TYPE = "type";
		
		/** @see android.provider.Contacts.ContactMethodsColumns.AUX_DATA */
		public static final String AUX_DATA = "aux_data";
		
		/** @see android.provider.Contacts.ContactMethodsColumns.DATA */
		public static final String DATA = "data";
		
		/** @see android.provider.Contacts.ContactMethodsColumns.KIND */
		public static final String KIND = "kind";
		
	}

	/**
	 * A sub directory containing the phone numbers of this contact
	 * 
	 * @author Michel Albert <michel@albert.lu>
	 */
	public interface PhoneColumns {
		
		/** @see android.provider.Contacts.PhonesColumns.IS_PRIMARY */
		String IS_PRIMARY = "is_primary";
		
		/** @see android.provider.Contacts.PhonesColumns.LABEL */
		String LABEL = "label";
		
		/** @see android.provider.Contacts.PhonesColumns.NUMBER */
		String NUMBER = "number";
		
		/** @see android.provider.Contacts.PhonesColumns.NUMBER_KEY */
		String NUMBER_KEY = "number_key";
		
		/** @see android.provider.Contacts.PhonesColumns.TYPE */
		String TYPE = "type";
		
	}

	/**
	 * A sub directory containing the organisations tied to this contact
	 * 
	 * @author Michel Albert <michel@albert.lu>
	 */
	public interface OrganizationColumns {

		/** @see android.provider.Contacts.OrganizationColumns.IS_PRIMARY */
		String IS_PRIMARY = "is_primary";

		/** @see android.provider.Contacts.OrganizationColumns.LABEL */
		String LABEL = "label";

		/** @see android.provider.Contacts.OrganizationColumns.TITLE */
		String TITLE = "title";

		/** @see android.provider.Contacts.OrganizationColumns.COMPANY */
		String COMPANY = "company";

		/** @see android.provider.Contacts.OrganizationColumns.TYPE */
		String TYPE = "type";

	}
}
