package lu.albert.android.contactbackup;

import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;

/**
 * This activity displays the license text in HTML which is much easier to read
 * than plain-text
 * 
 * @author exhuma
 */
public class LicenseViewer extends Activity {
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.html_view);
		loadDocument();
	}

	private void loadDocument() {
		WebView view = (WebView)findViewById(R.id.webview);
		InputStream license_stream = getResources().openRawResource(R.raw.gpl3);
		StringBuilder content = new StringBuilder();
		int result;
		try {
			result = license_stream.read();
			while ( result != -1 ){
				content.append((char)result);
				result = license_stream.read();
			}
		} catch (IOException e) {
			// TODO: handle exception
			Log.e(this.getClass().getCanonicalName(), e.getMessage());
		}
		view.loadData(content.toString(), "text/html", "UTF-8");
	}
	
}
