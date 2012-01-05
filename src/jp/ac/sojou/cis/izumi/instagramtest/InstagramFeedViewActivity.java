package jp.ac.sojou.cis.izumi.instagramtest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.ImageView;

public class InstagramFeedViewActivity extends Activity {

	/**
	 * �F�ؗpURL
	 */
	private static final String OAUTH_AUTHORIZE_URL = "https://api.instagram.com/oauth/authorize/";

	/**
	 * �t�B�[�h�擾�pURL
	 */
	private static final String USERS_SELF_FEED_URL = "https://api.instagram.com/v1/users/self/feed";

	/**
	 * �R�[���o�b�NURL - AndroidManifest.xml�ł̐ݒ肪�K�v
	 */
	private static final String OAUTH_CALLBACK_URL = "instagram://callback";

	/**
	 * Instagram�ɓo�^�����A�v���̃N���C�A���gID
	 */
	private static final String CLIENT_ID = "227b92a281464186be493c677d4f4f41";

	/**
	 * ��x�Ɏ擾����t�B�[�h�̐�
	 */
	private static final int NUM_FEEDS = 20;
	/**
	 * �摜�̍X�V���s���ŏ�����
	 */
	private static final int VIEW_UPDATE_DURATION = 10 * 1000;
	/**
	 * Instagram����t�B�[�h���擾����Ԋu
	 */
	private static final int INATAGRAM_UPDATE_DURATION = 5 * 60 * 1000;

	/**
	 * �摜��\������r���[
	 */
	private ImageView mImageView;
	/**
	 * �A�N�Z�X�g�[�N��
	 */
	private String token;

	/**
	 * Instagram����擾�����摜URL�̃��X�g
	 */
	private List<String> imageUrls;
	/**
	 * ���ݕ\�����Ă���摜URL�̔ԍ�
	 */
	private int imageUrlPointer = 0;
	/**
	 * �摜�̍X�V���������t���O
	 */
	private boolean imageUpdating = false;

	/**
	 * ������Ԃ�ێ�
	 */
	private static State state = State.DEFAULT;

	/**
	 * ������Ԃ�\����(enum)
	 */
	private enum State {
		DEFAULT, // �������
		GET_ACCESS_TOKEN, // �A�N�Z�X�g�[�N���̎擾�v��
		ACCESS_TOKEN_REQUESTED, // �A�N�Z�X�g�[�N���̎擾�v������
		HAS_ACCESS_TOKEN // �A�N�Z�X�g�[�N����ێ����Ă���
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		Log.d("TEST", "OnCreate");

		mImageView = (ImageView) findViewById(R.id.imageView);

	}

	@Override
	protected void onResume() {
		super.onResume();

		// �A�N�Z�X�g�[�N�����A�v���P�[�V�����ݒ肩��擾
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		token = prefs.getString("access_token", null);
		Log.d("TEST", "" + token);
		Log.d("TEST", state.name());

		// �ۑ�����Ă���A�N�Z�X�g�[�N�����`�F�b�N
		if (state != State.ACCESS_TOKEN_REQUESTED) {
			if (token == null) {
				state = State.GET_ACCESS_TOKEN;
			} else {
				state = State.HAS_ACCESS_TOKEN;
			}
		}

		// �A�N�Z�X�g�[�N���̎擾�v��
		if (state == State.GET_ACCESS_TOKEN) {
			String url = OAUTH_AUTHORIZE_URL + "?client_id=" + CLIENT_ID
					+ "&redirect_uri=" + URLEncoder.encode(OAUTH_CALLBACK_URL)
					+ "&response_type=token";
			Log.d("TEST", url);

			state = State.ACCESS_TOKEN_REQUESTED;
			// �u���E�U���N��
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
		}

		// �A�N�Z�X�g�[�N���̎擾�v���ɑ΂��鉞��
		if (state == State.ACCESS_TOKEN_REQUESTED) {
			Uri uri = getIntent().getData();
			if (uri != null) {
				String uripath = uri.toString();
				Log.d("TEST", uripath);

				token = uripath.split("#")[1];
				Log.d("TEST", token);
				prefs.edit().putString("access_token", token).commit();
				state = State.HAS_ACCESS_TOKEN;
			}
		}

		if (state == State.HAS_ACCESS_TOKEN) {
			// Instagram�����莞�Ԃ��Ƃɉ摜���擾���Ă���^�C�}�[�^�X�N
			TimerTask instagramReadTimerTask = new TimerTask() {

				@Override
				public void run() {
					getImages(token);
				}
			};
			Timer instagramReadTimer = new Timer();
			instagramReadTimer.scheduleAtFixedRate(instagramReadTimerTask, 0,
					INATAGRAM_UPDATE_DURATION);

			// GUI�X�V�̂��߂̃n���h��
			final Handler handler = new Handler();

			// �摜���X�V����^�C�}�[�^�X�N
			TimerTask imageUpdateTimerTask = new TimerTask() {
				@Override
				public void run() {
					if (imageUpdating)
						return;

					imageUpdating = true;
					Log.d("TEST", "imageUpdating");
					final Bitmap bm = nextImage();
					Log.d("TEST", "" + bm);
					if (bm != null) {
						handler.post(new Runnable() {
							@Override
							public void run() {
								Log.d("TEST", "setImageBitmap");
								mImageView.setImageBitmap(bm);
								mImageView.invalidate();
							}
						});
					}
					imageUpdating = false;
				}
			};
			Timer imageUpdateTimer = new Timer();
			imageUpdateTimer.scheduleAtFixedRate(imageUpdateTimerTask, 0,
					VIEW_UPDATE_DURATION);
		}
	}

	/**
	 * ���ɕ\������摜���擾
	 * 
	 * @return
	 */
	private Bitmap nextImage() {

		if (imageUrls == null) {
			return null;
		}

		try {
			String u = imageUrls.get(imageUrlPointer);
			Bitmap bmp = BitmapFactory
					.decodeStream(connect(u).getInputStream());
			imageUrlPointer = (imageUrlPointer + 1) % imageUrls.size();

			return bmp;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Instagram����摜��URL���擾
	 * 
	 * @param token
	 */
	private void getImages(String token) {
		try {
			// �ŐV�̃t�B�[�hn���擾
			URL url = new URL(USERS_SELF_FEED_URL + "?" + token + "&count="
					+ NUM_FEEDS);
			Log.d("TEST", "" + url);
			HttpURLConnection connect = connect(url);
			BufferedReader br = new BufferedReader(new InputStreamReader(
					connect.getInputStream()));
			StringBuilder sb = new StringBuilder();
			String line = null;
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}

			List<String> ius = new ArrayList<String>();

			// JSON�Ƃ��ď���
			JSONObject jo = new JSONObject(sb.toString());
			Log.d("TEST", "" + jo.toString());
			JSONArray ja = jo.getJSONArray("data");
			for (int i = 0; i < ja.length(); i++) {
				// �t�B�[�h����摜URL�����o�����X�g�Ɋi�[
				String imageUrl = ja.getJSONObject(i).getJSONObject("images")
						.getJSONObject("standard_resolution").getString("url");
				Log.d("TEST", "" + imageUrl);
				ius.add(imageUrl);
			}
			imageUrlPointer = 0;
			imageUrls = ius;

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * �ؖ��������؂����ɂ��ׂẴT�[�o�[��M�����܂�
	 */
	private final static HostnameVerifier DO_NOT_VERIFY = new HostnameVerifier() {
		public boolean verify(String hostname, SSLSession session) {
			return true;
		}
	};

	/**
	 * �ؖ��������؂����ɂ��ׂẴT�[�o�[��M�����܂�
	 */
	private static void trustAllHosts() {
		// Create a trust manager that does not validate certificate chains
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return new java.security.cert.X509Certificate[] {};
			}

			public void checkClientTrusted(X509Certificate[] chain,
					String authType) throws CertificateException {
			}

			public void checkServerTrusted(X509Certificate[] chain,
					String authType) throws CertificateException {
			}
		} };

		// Install the all-trusting trust manager
		try {
			SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection
					.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * URL����Http�ڑ����擾���܂�
	 */
	private HttpURLConnection connect(URL url) throws IOException {
		HttpURLConnection http = null;

		if (url.getProtocol().toLowerCase().equals("https")) {
			trustAllHosts();
			HttpsURLConnection https = (HttpsURLConnection) url
					.openConnection();
			https.setHostnameVerifier(DO_NOT_VERIFY);
			http = https;
		} else {
			http = (HttpURLConnection) url.openConnection();
		}

		return http;
	}

	/**
	 * URL�����񂩂�Http�ڑ����擾���܂�
	 */
	private HttpURLConnection connect(String u) {
		try {
			return connect(new URL(u));
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}