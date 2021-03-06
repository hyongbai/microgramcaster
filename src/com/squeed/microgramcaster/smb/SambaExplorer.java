package com.squeed.microgramcaster.smb;

import java.io.IOException;
import java.net.MalformedURLException;

import jcifs.smb.SmbAuthException;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.util.Log;

import com.squeed.microgramcaster.Constants;
import com.squeed.microgramcaster.MainActivity;
import com.squeed.microgramcaster.R;
import com.squeed.microgramcaster.media.MediaItem;
import com.squeed.microgramcaster.media.MediaItemComparator;
import com.squeed.microgramcaster.source.NetworkSourceItem;
import com.squeed.microgramcaster.util.PathStack;
import com.squeed.microgramcaster.util.VideoTypes;

public class SambaExplorer {
	
	private static final String TAG = "SambaExplorer";

	private MainActivity mainActivity;
	private SharedPreferences preferences;
	//private Stack<String> containerStack = new Stack<String>();
	
	private String mHost;
//	private int curListID = 0;
//
//	private boolean active;
//	private String IPsubnet;

	public SambaExplorer(MainActivity mainActivity) {
		this.mainActivity = mainActivity;
		this.preferences = PreferenceManager.getDefaultSharedPreferences(mainActivity);
	}

//	private static String getIPsubnet(int addr) {
//		StringBuffer buf = new StringBuffer();
//		buf.append(addr & 0xff).append('.').append((addr >>>= 8) & 0xff).append('.').append((addr >>>= 8) & 0xff)
//				.append('.');
//		return buf.toString();
//	}
	
	public void init() {
		
		
		
		ConnectivityManager cm = (ConnectivityManager) mainActivity.getSystemService(mainActivity.CONNECTIVITY_SERVICE);
		NetworkInfo ni = cm.getActiveNetworkInfo();
		if (ni.getType() == ConnectivityManager.TYPE_MOBILE) {
			mainActivity.runOnUiThread(new Runnable() {

				@Override
				public void run() {
					//new AlertDialog.Builder(mainActivity).setMessage("This application is meant for WIFI networks.").show();
					AlertDialog dialog = new AlertDialog.Builder(mainActivity).create();
					dialog.setTitle("No WiFi");
					dialog.setMessage("SMB scanning requires a WiFi connection.");
					dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Ok", new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
					});
					dialog.show();
				} 
				
			});
			return;
		}

		WifiManager wifi = (WifiManager) mainActivity.getSystemService(mainActivity.WIFI_SERVICE);
		//DhcpInfo info = wifi.getDhcpInfo();
		//IPsubnet = getIPsubnet(info.ipAddress);

		mHost = "";

		jcifs.Config.setProperty("jcifs.encoding", "Cp1252");
		jcifs.Config.setProperty("jcifs.smb.lmCompatibility", "0");
		jcifs.Config.setProperty("jcifs.netbios.hostname", "AndroidPhone");

		jcifs.Config.registerSmbURLHandler();

		if (!mHost.startsWith("smb:/")) {
			if (mHost.startsWith("/")) {
				mHost = "smb:/" + mHost + "/";
			} else {
				mHost = "smb://" + mHost + "/";
			}
		}
		
		PathStack.clearContainerIdStack();// ().clear();
		PathStack.get().push(mHost);	

		SmbFile f;
		try {
			f = new SmbFile(mHost);
							
			if (f.canRead()) {
				traverseSMB(f, 2);
			}
		} catch (SmbAuthException e) {
			Log.e("SambaExplorer", "SmbAuthException: " + e.getMessage());
		} catch (MalformedURLException e) {
			Log.e(TAG, "MalformedURLException initializing SMB explorer: " + e.getMessage());
		} catch (SmbException e) {
			Log.e(TAG, "SmbException initializing SMB explorer: " + e.getMessage());
		} catch (IOException e) {
			Log.e(TAG, "IOException initializing SMB explorer: " + e.getMessage());
		}
	}
	


	private Runnable updateAdapter = new Runnable() {
		@Override
		public void run() {
			mainActivity.getMediaItemListAdapter().notifyDataSetChanged();
		}
	};

	public void forceUpdate() {
		mainActivity.runOnUiThread(updateAdapter);
	}

	
	private MediaItemComparator comparator = new MediaItemComparator();

	public void traverseSMB(SmbFile f, int depth) throws MalformedURLException, IOException {

		if (depth == 0) {
			return;
		}
		
		try {
			SmbFile[] l;

			l = f.listFiles();

			for (int i = 0; l != null && i < l.length; i++) {
				try {
					if (l[i].isDirectory()) {
	
						String path = l[i].getCanonicalPath();
						String name = null;
						if(path.endsWith("/")) {
							String tmpPath = path.substring(0, path.length() - 1);
							name = tmpPath.substring(tmpPath.lastIndexOf("/")+1);
						} else {
							name = path.substring(path.lastIndexOf("/")+1);
						}
						final MediaItem mi = new MediaItem();
						mi.setType(Constants.SMB_FOLDER);
						mi.setData(path);
						mi.setName(name);
						mi.setDuration(null);
						mi.setThumbnail(BitmapFactory.decodeResource(mainActivity.getResources(), R.drawable.ic_menu_archive));
				
						mainActivity.runOnUiThread(new Runnable() {

							@Override
							public void run() {
								mainActivity.getMediaItemListAdapter().add(mi);
								mainActivity.getMediaItemListAdapter().sort(comparator);
								mainActivity.getMediaItemListAdapter().notifyDataSetChanged();
							}
							
						});
					} else {
						if((VideoTypes.isPlayableVideo(l[i].getCanonicalPath().toLowerCase())) 
								|| preferences.getBoolean("show_unplayable", false)) {

							final MediaItem mi = new MediaItem();
							mi.setType(Constants.SMB_FILE);
							mi.setData(l[i].getCanonicalPath());
							mi.setName(l[i].getCanonicalPath().substring(l[i].getCanonicalPath().lastIndexOf("/")+1));
							mi.setDuration((long) l[i].length());
							if(VideoTypes.isVideo(mi.getName())) {
								mi.setThumbnail(BitmapFactory.decodeResource(mainActivity.getResources(), R.drawable.ic_action_video));	
							} else {
								mi.setThumbnail(BitmapFactory.decodeResource(mainActivity.getResources(), R.drawable.ic_action_cancel));
							}							
							
							mainActivity.runOnUiThread(new Runnable() {

								@Override
								public void run() {
									mainActivity.getMediaItemListAdapter().add(mi);
									mainActivity.getMediaItemListAdapter().sort(comparator);
									mainActivity.getMediaItemListAdapter().notifyDataSetChanged();
								}								
							});
						}
					}

				} catch (SmbAuthException e) {
					// Here we could launch an Activity to enter username/password for a SMB share
					Log.e(TAG, "SmbAuthException: " + e.getMessage());
				} catch (IOException ioe) {

				}
			}

		} catch (SmbAuthException e) {
			// Here we could launch an Activity to enter username/password for a SMB share
		} catch (Exception e) {
			Log.e(TAG, "Exception: " + e.getMessage());
		}
	}

	public void handleNetworkSourceSelected(NetworkSourceItem item) {
		
	}
	
	String getParentContainerIdFromStack() {
		if(PathStack.get().size() > 1) {
			return PathStack.get().elementAt(PathStack.get().size() - 2); // Peek the PARENT containerId
		} else if(PathStack.get().size() == 1) {
			return PathStack.get().elementAt(PathStack.get().size() - 1);
		} else {
			return "smb://";	
		}
	}

	
//	public void popContainerIdStack() {
//		containerStack.pop();
//	}
//	
//	public void clearContainerIdStack() {
//		containerStack.clear();
//	}
//
//	public Stack<String> getContainerStack() {
//		return containerStack;
//	}

}
