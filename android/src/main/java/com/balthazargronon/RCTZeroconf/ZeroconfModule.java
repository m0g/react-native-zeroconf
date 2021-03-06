package com.balthazargronon.RCTZeroconf;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import javax.annotation.Nullable;

import java.util.Locale;
import java.util.Map;
import java.io.UnsupportedEncodingException;

/**
 * Created by Jeremy White on 8/1/2016.
 * Copyright © 2016 Balthazar Gronon MIT
 */
public class ZeroconfModule extends ReactContextBaseJavaModule {



    public static final String EVENT_START = "RNZeroconfStart";
    public static final String EVENT_STOP = "RNZeroconfStop";
    public static final String EVENT_ERROR = "RNZeroconfError";
    public static final String EVENT_FOUND = "RNZeroconfFound";
    public static final String EVENT_REMOVE = "RNZeroconfRemove";
    public static final String EVENT_RESOLVE = "RNZeroconfResolved";

    public static final String KEY_SERVICE_NAME = "name";
    public static final String KEY_SERVICE_FULL_NAME = "fullName";
    public static final String KEY_SERVICE_HOST = "host";
    public static final String KEY_SERVICE_PORT = "port";
    public static final String KEY_SERVICE_ADDRESSES = "addresses";
    public static final String KEY_SERVICE_TXT = "txt";

    protected NsdManager mNsdManager;
    protected NsdManager.DiscoveryListener mDiscoveryListener;
    protected NsdManager.RegistrationListener mRegistrationListener;

    public ZeroconfModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "RNZeroconf";
    }

    @ReactMethod
    public void register(String type, String protocol, String domain) {

        if (mNsdManager == null) {
            mNsdManager = (NsdManager) getReactApplicationContext().getSystemService(Context.NSD_SERVICE);
        }

        mRegistrationListener = new NsdManager.RegistrationListener() {

            @Override
            public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) {
                // Save the service name. Android may have changed it in order to
                // resolve a conflict, so update the name you initially requested
                // with the name Android actually used.
                //mServiceName = NsdServiceInfo.getServiceName();
                Log.i("ReactNative", "Service registered");
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Registration failed! Put debugging code here to determine why.
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo arg0) {
                // Service has been unregistered. This only happens when you call
                // NsdManager.unregisterService() and pass in this listener.
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Unregistration failed. Put debugging code here to determine why.
            }
        };

//        String serviceType = String.format("_ht\cltp._tcp.");
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName("NsdChat");
        serviceInfo.setServiceType("_http._tcp.");
        serviceInfo.setPort(9999);

        mNsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, mRegistrationListener);
    }

    @ReactMethod
    public void scan(String type, String protocol, String domain) {

        Log.i("YO", "Wesh wesh LOGGING TIME!");

        if (mNsdManager == null) {
            mNsdManager = (NsdManager) getReactApplicationContext().getSystemService(Context.NSD_SERVICE);
        }

//        this.stop();

        mDiscoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                String error = "Starting service discovery failed with code: " + errorCode;
                sendEvent(getReactApplicationContext(), EVENT_ERROR, error);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                String error = "Stopping service discovery failed with code: " + errorCode;
                sendEvent(getReactApplicationContext(), EVENT_ERROR, error);
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                Log.i("ReactNative", "EVENT START!");
                sendEvent(getReactApplicationContext(), EVENT_START, null);
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                sendEvent(getReactApplicationContext(), EVENT_STOP, null);
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                Log.i("ReactNative", "EVENT FOUND!");
                WritableMap service = new WritableNativeMap();
                service.putString(KEY_SERVICE_NAME, serviceInfo.getServiceName());

                sendEvent(getReactApplicationContext(), EVENT_FOUND, service);
                mNsdManager.resolveService(serviceInfo, new ZeroResolveListener());
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                WritableMap service = new WritableNativeMap();
                service.putString(KEY_SERVICE_NAME, serviceInfo.getServiceName());
                sendEvent(getReactApplicationContext(), EVENT_REMOVE, service);
            }
        };

        String serviceType = String.format("_%s._%s.", type, protocol);
        mNsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    @ReactMethod
    public void stop() {
        if (mDiscoveryListener != null) {
            mNsdManager.stopServiceDiscovery(mDiscoveryListener);
        }
        mDiscoveryListener = null;
    }

    protected void sendEvent(ReactContext reactContext,
                             String eventName,
                             @Nullable Object params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    private class ZeroResolveListener implements NsdManager.ResolveListener {
        @Override
        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE) {
                mNsdManager.resolveService(serviceInfo, this);
            } else {
                String error = "Resolving service failed with code: " + errorCode;
                sendEvent(getReactApplicationContext(), EVENT_ERROR, error);
            }
        }

        @Override
        public void onServiceResolved(NsdServiceInfo serviceInfo) {
            WritableMap service = new WritableNativeMap();
            service.putString(KEY_SERVICE_NAME, serviceInfo.getServiceName());
            service.putString(KEY_SERVICE_FULL_NAME, serviceInfo.getHost().getHostName() + serviceInfo.getServiceType());
            service.putString(KEY_SERVICE_HOST, serviceInfo.getHost().getHostName());
            service.putInt(KEY_SERVICE_PORT, serviceInfo.getPort());

            WritableMap txtRecords = new WritableNativeMap();

            Map<String, byte[]> attributes = serviceInfo.getAttributes();
            for (String key : attributes.keySet()) {
              try {
                byte[] recordValue = attributes.get(key);
                txtRecords.putString(String.format(Locale.getDefault(), "%s", key), String.format(Locale.getDefault(), "%s", recordValue != null ? new String(recordValue, "UTF_8") : ""));
              } catch (UnsupportedEncodingException e) {
                String error = "Failed to encode txtRecord: " + e;
                sendEvent(getReactApplicationContext(), EVENT_ERROR, error);
              }
            }

            service.putMap(KEY_SERVICE_TXT, txtRecords);

            WritableArray addresses = new WritableNativeArray();
            addresses.pushString(serviceInfo.getHost().getHostAddress());

            service.putArray(KEY_SERVICE_ADDRESSES, addresses);

            sendEvent(getReactApplicationContext(), EVENT_RESOLVE, service);
        }
    }

    @Override
    public void onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy();
        stop();
    }
}
