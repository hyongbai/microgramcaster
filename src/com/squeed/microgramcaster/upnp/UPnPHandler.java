package com.squeed.microgramcaster.upnp;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.squeed.microgramcaster.MainActivity;
import com.squeed.microgramcaster.source.NetworkSourceArrayAdapter;
import com.squeed.microgramcaster.source.NetworkSourceItem;
import com.squeed.microgramcaster.source.NetworkSourceType;
import com.squeed.microgramcaster.util.PathStack;


public class UPnPHandler {
	 private BrowseRegistryListener registryListener = new BrowseRegistryListener();

	    private AndroidUpnpService upnpService;

		private MainActivity activity;
		private ContentListingBuilder dlnaContentListingBuilder;

		private static final String TAG = "UPnPHandler";
	    
	    public UPnPHandler(MainActivity activity) {
			this.activity = activity;
	    }

	    private ServiceConnection serviceConnection = new ServiceConnection() {

	        public void onServiceConnected(ComponentName className, IBinder service) {
	            upnpService = (AndroidUpnpService) service;
	            
	            // Clear the list
	           // listAdapter.clear();

	            // Get ready for future device advertisements
	            upnpService.getRegistry().addListener(registryListener);

	            // Now add all devices to the list we already know about
	            for (Device device : upnpService.getRegistry().getDevices()) {
	                registryListener.deviceAdded(device);
	            }
	            getNetworkSourceArrayAdapter().notifyDataSetChanged();

	            // Search asynchronously for all devices, they will respond soon
	            upnpService.getControlPoint().search();	            
	        }

	        public void onServiceDisconnected(ComponentName className) {
	            upnpService = null;
	        }
	    };
		
		
	    
	    protected class BrowseRegistryListener extends DefaultRegistryListener {


			/* Discovery performance optimization for very slow Android devices! */
	        @Override
	        public void remoteDeviceDiscoveryStarted(Registry registry, RemoteDevice device) {
	            deviceAdded(device);
	        }

	        @Override
	        public void remoteDeviceDiscoveryFailed(Registry registry, final RemoteDevice device, final Exception ex) {
	            deviceRemoved(device);
	        }
	        /* End of optimization, you can remove the whole block if your Android handset is fast (>= 600 Mhz) */

	        @Override
	        public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
	            deviceAdded(device);
	        }

	        @Override
	        public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
	            deviceRemoved(device);
	        }

	        @Override
	        public void localDeviceAdded(Registry registry, LocalDevice device) {
	            deviceAdded(device);
	        }

	        @Override
	        public void localDeviceRemoved(Registry registry, LocalDevice device) {
	            deviceRemoved(device);
	        }

	        public void deviceAdded(final Device device) {
	        	activity.runOnUiThread(new Runnable() {
	                public void run() {
	                    DeviceDisplay d = new DeviceDisplay(device);
	                    String name =
	                    		device.getDetails() != null && device.getDetails().getFriendlyName() != null
	                                ? device.getDetails().getFriendlyName()
	                                : device.getDisplayString();
	                    NetworkSourceItem item = new NetworkSourceItem(name, d.getDevice().getDetails().getManufacturerDetails().getManufacturer(), NetworkSourceType.UPNP, d);
	                    int position = getNetworkSourceArrayAdapter().getPosition(item);
	                    if (position >= 0) {
	                        // Device already in the list, re-set new value at same position
	                    	getNetworkSourceArrayAdapter().remove(item);
	                    	getNetworkSourceArrayAdapter().insert(item, position);
	                    } else {
	                    	getNetworkSourceArrayAdapter().add(item);
	                    }
	                    getNetworkSourceArrayAdapter().notifyDataSetChanged();
	                    Log.i(TAG, "Added UPNP device: " + d.toString() +  "\n\n" + d.getDetailsMessage());
	                }
	            });
	        }

	        public void deviceRemoved(final Device device) {
	        	activity.runOnUiThread(new Runnable() {
	                public void run() {
	                	DeviceDisplay d = new DeviceDisplay(device);
	                	NetworkSourceItem item = new NetworkSourceItem(d.getDevice().getDisplayString(), d.getDevice().getDetails().getManufacturerDetails().getManufacturer(), NetworkSourceType.UPNP, d);
	                    
	                    getNetworkSourceArrayAdapter().remove(item);
	                	Log.i("UPnPHandler", "Removed UPNP device: " + device.getDisplayString());
	                }
	            });
	        }
	    }

	    private NetworkSourceArrayAdapter getNetworkSourceArrayAdapter() {
			return activity.getNetworkSourceArrayAdapter();
		}
	   
	    public void initUPnpService() {
	    	//getNetworkSourceArrayAdapter() = new UPnPDeviceAdapter(activity, R.layout.upnp_device_listview_item, new ArrayList<DeviceDisplay>());
			// This will start the UPnP service if it wasn't already started
	    	
	    	activity.getApplicationContext().bindService(
    	            new Intent(activity, BrowserUpnpService.class),
    	            serviceConnection,
    	            Context.BIND_AUTO_CREATE);
    		
	        
	        dlnaContentListingBuilder = new ContentListingBuilder(activity, this);
		}
	    
	    public void destroyUPnpService() {
	    	
	    	if (upnpService != null) {
	    		upnpService.getRegistry().removeListener(registryListener);
	    		upnpService.getConfiguration().shutdown();
	    	}
	        // This will stop the UPnP service if nobody else is bound to it
	    	activity.getApplicationContext().unbindService(serviceConnection);	    	
	    }
	    
	    public void searchUPnp() {
	    	getNetworkSourceArrayAdapter().clear();
	    	getNetworkSourceArrayAdapter().notifyDataSetChanged();
	    	
	    	
	    	if (upnpService != null) {
	    		upnpService.getRegistry().removeAllRemoteDevices();
	            upnpService.getControlPoint().search();
	    	} else {
	    		// Probably unnecessary...
	    		// Toast.makeText(activity, "UPnpService not initialized.", Toast.LENGTH_LONG).show();
	    	}
	    	activity.showNetworkSourceDialog();
	    }
	  
		private RemoteService currentService;


		public Service getCurrentService() {
			return currentService;
		}

		public AndroidUpnpService getUPnPService() {
			return upnpService;
		}

		public void buildContentListing(String containerId) {
			dlnaContentListingBuilder.buildFolderListing(containerId);
		}

		public void handleUpPressed() {
			PathStack.popContainerIdStack();
		}

		public void handleNetworkSourceSelected(NetworkSourceItem item) {
			if(item.getNetworkObject() != null && item.getNetworkObject() instanceof DeviceDisplay) {
				DeviceDisplay dd = (DeviceDisplay) item.getNetworkObject();
				Device unspecDevice = dd.getDevice();
				if(unspecDevice instanceof RemoteDevice) {
					RemoteDevice currentDevice = (RemoteDevice) unspecDevice; 
					for (RemoteService service : currentDevice.getServices()) {
		                if (service.getServiceType().getType().equals("ContentDirectory")) {
		                	currentService = service;	
		                	//dlnaContentListingBuilder.clearContainerIdStack();
		                	PathStack.clearContainerIdStack();
		                	dlnaContentListingBuilder.buildFolderListing(null);
		                }
		            }
				}
			}
		}
}
