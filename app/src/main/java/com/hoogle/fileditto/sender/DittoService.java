package com.hoogle.fileditto.sender;

import android.app.NotificationChannel;
import android.content.BroadcastReceiver;
import android.content.Context;

import android.content.Intent;
import android.content.IntentFilter;
import static com.hoogle.fileditto.Utils.Utils.DEFAULT_PORT_OREO;

import android.graphics.Color;
import android.os.Build;
import android.os.Message;
import android.provider.Settings;

import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.Log;

import android.support.v4.app.NotificationCompat;

import com.hoogle.fileditto.R;
import com.hoogle.fileditto.Utils.HotspotControl;

import com.hoogle.fileditto.Utils.WifiUtils;


import android.support.v4.app.NotificationCompat;
import java.lang.ref.WeakReference;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Base64;




import java.lang.ref.WeakReference;


import static com.hoogle.fileditto.sender.DittoService.ShareIntents.SHARE_CLIENT_IP;
import static com.hoogle.fileditto.sender.DittoService.ShareIntents.SHARE_SERVER_UPDATES_INTENT_ACTION;
import static com.hoogle.fileditto.sender.DittoService.ShareIntents.SHARE_SERVER_UPDATE_FILE_NAME;
import static com.hoogle.fileditto.sender.DittoService.ShareIntents.SHARE_SERVER_UPDATE_TEXT;
import static com.hoogle.fileditto.sender.DittoService.ShareIntents.SHARE_TRANSFER_PROGRESS;
import static com.hoogle.fileditto.sender.DittoService.ShareIntents.TYPE;
import static com.hoogle.fileditto.sender.DittoService.ShareIntents.Types.AP_DISABLED_ACKNOWLEDGEMENT;
import static com.hoogle.fileditto.sender.DittoService.ShareIntents.Types.FILE_TRANSFER_STATUS;

import static com.hoogle.fileditto.Utils.Utils.isOreoOrAbove;

public class DittoService extends Service {

    private static final String TAG = "ShareService";
    private Context mContext;
    private NotificationManager mNotificationManager;
    private NotificationCompat.Builder mBuilder;
    public static final String NOTIFICATION_CHANNEL_ID = "10001";

    public DittoService(Context context) {
        mContext = context;
    }
    private WifiManager wifiManager;
    private HotspotControl hotspotControl;
    private DittoServer m_fileServer;
    private BroadcastReceiver m_notificationStopActionReceiver;
    private HotspotChecker hotspotCheckHandler;

    private static final int SHARE_SERVICE_NOTIFICATION_ID = 100001;
    static final String WIFI_AP_ACTION_START = "wifi_ap_start";
    static final String WIFI_AP_ACTION_STOP = "wifi_ap_stop";
    static final String WIFI_AP_ACTION_START_CHECK = "wifi_ap_check";

    public static final String EXTRA_FILE_PATHS = "file_paths";
    public static final String EXTRA_PORT = "server_port";
    public static final String EXTRA_SENDER_NAME = "sender_name";

    private static final int AP_ALIVE_CHECK = 100;
    private static final int AP_START_CHECK = 101;

    static class ShareIntents {
        static final String TYPE = "type";
        static final String SHARE_SERVER_UPDATES_INTENT_ACTION = "share_server_updates_intent_action";
        static final String SHARE_SERVER_UPDATE_TEXT = "share_server_update_text";
        static final String SHARE_SERVER_UPDATE_FILE_NAME = "share_server_file_name";
        static final String SHARE_CLIENT_IP = "share_client_ip";
        static final String SHARE_TRANSFER_PROGRESS = "share_transfer_progress";

        class Types {
            static final int FILE_TRANSFER_STATUS = 1000;
            static final int AP_ENABLED_ACKNOWLEDGEMENT = 1001;
            static final int AP_DISABLED_ACKNOWLEDGEMENT = 1002;
        }
    }

    public DittoService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        hotspotControl = HotspotControl.getInstance(getApplicationContext());
        m_notificationStopActionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (null != intent && WIFI_AP_ACTION_STOP.equals(intent.getAction()))
                    disableHotspotAndStop();
            }
        };
        registerReceiver(m_notificationStopActionReceiver, new IntentFilter(WIFI_AP_ACTION_STOP));
        //Start a foreground with message saying 'Initiating Hotspot'. Message is later updated using SHARE_SERVICE_NOTIFICATION_ID
        startForeground(SHARE_SERVICE_NOTIFICATION_ID, getNotification(getString(R.string.p2p_sender_service_init_notification_header), false));
        hotspotCheckHandler = new HotspotChecker(this);

    }

    protected android.support.v4.app.NotificationCompat.Action getStopAction() {
        Intent intent = new Intent(WIFI_AP_ACTION_STOP);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        return new android.support.v4.app.NotificationCompat.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingIntent).build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (null != intent) {
            String action = intent.getAction();
            switch (action) {
                case WIFI_AP_ACTION_START:
                    if (!hotspotControl.isEnabled()) {
                        startFileHostingServer(intent.getStringArrayExtra(EXTRA_FILE_PATHS), intent.getIntExtra(EXTRA_PORT, 0), intent.getStringExtra(EXTRA_SENDER_NAME));
                    }
//                    sendAcknowledgementBroadcast(AP_ENABLED_ACKNOWLEDGEMENT);
                    break;
                case WIFI_AP_ACTION_STOP:
                    disableHotspotAndStop();
                    break;
                case WIFI_AP_ACTION_START_CHECK:
                    if (null != hotspotControl && hotspotControl.isEnabled()) {
                        //starts a handler in loop to check Hotspot check. Service kills itself when Hotspot is no more alive
                        if (null == hotspotCheckHandler)
                            hotspotCheckHandler = new HotspotChecker(this);
                        else hotspotCheckHandler.removeMessages(AP_ALIVE_CHECK);
                        hotspotCheckHandler.sendEmptyMessageDelayed(100, 3000);
                    }
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    private void disableHotspotAndStop() {
        Log.d(TAG, "p2p service stop action received..");
        if (null != hotspotControl)
            hotspotControl.disable();
        wifiManager.setWifiEnabled(true);
        if (null != hotspotCheckHandler)
            hotspotCheckHandler.removeCallbacksAndMessages(null);
        stopFileTasks();
        stopForeground(true);
        sendAcknowledgementBroadcast(AP_DISABLED_ACKNOWLEDGEMENT);
        stopSelf();

    }
    @RequiresApi(api = Build.VERSION_CODES.O)
    void makeNotificationChannel(String id, String name, int importance)
    {
        NotificationChannel channel = new NotificationChannel(id, name, importance);
        channel.setShowBadge(true); // set false to disable badges, Oreo exclusive

        NotificationManager notificationManager =
                (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        assert notificationManager != null;
        notificationManager.createNotificationChannel(channel);
    }

    private Notification getNotification(String text, boolean addStopAction) {
        Intent notificationIntent = new Intent(getApplicationContext(),
                DittoActivity.class);

        // make the channel. The method has been discussed before.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            makeNotificationChannel("CHANNEL_1", "Example channel", NotificationManager.IMPORTANCE_DEFAULT);
        }
        // the check ensures that the channel will only be made
        // if the device is running Android 8+

        NotificationCompat.Builder notification =
                new NotificationCompat.Builder(this, "CHANNEL_1");
        // the second parameter is the channel id.
        // it should be the same as passed to the makeNotificationChannel() method

        notification
                .setSmallIcon(R.mipmap.ic_launcher).setWhen(System.currentTimeMillis())
                .setContentTitle(getString(R.string.app_name))
                .setTicker(text)
                .setContentText(text);
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        assert notificationManager != null;
        notificationManager.notify(1, notification.build());
        // it is better to not use 0 as notification id, so used 1.





        if (addStopAction)
            notification.addAction(getStopAction());
        return notification.build();
    }




    private void startFileHostingServer(String[] filePathsTobeServed, int port, final String sender_name) {
        m_fileServer = new DittoServer(getApplicationContext()
                , new StatusCheck() {
            @Override
            public synchronized void onBytesTransferProgress(final String ip, final String fileName, long totalSize, final String speed, long currentSize, final int percentage) {
                sendTransferStatusBroadcast(ip, percentage, "Transferring " + fileName + " file(" + percentage + "%)\nSpeed: " + speed, fileName);
            }

            @Override
            public void onBytesTransferCompleted(final String ip, final String fileName) {
                sendTransferStatusBroadcast(ip, 100, fileName + " file transfer completed", fileName);
            }

            @Override
            public synchronized void onBytesTransferStarted(final String ip, final String fileName) {
                sendTransferStatusBroadcast(ip, 0, "Transferring " + fileName + " file", fileName);
            }

            @Override
            public void onBytesTransferCancelled(final String ip, final String error, String fileName) {
                Log.e(TAG, " transfer cancelled for ip: " + ip + ", file name: " + fileName);
                sendTransferStatusBroadcast(ip, 0, "Error in file transfer: " + error, fileName);
            }
        }, filePathsTobeServed, port);
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    m_fileServer.start();
//
//                } catch (BindException e) {
//                    e.printStackTrace();
//                    Log.e(TAG, "exception in starting file server: " + e.getMessage());
//                } catch (Exception e) {
//                    Log.e(TAG, "exception in starting file server: " + e.getMessage());
//                    e.printStackTrace();
//                }
//            }
//        }).start();
        try {
            m_fileServer.start();
            Log.d(TAG, "**** Server started success****port: " + m_fileServer.getListeningPort() + ", " + m_fileServer.getHostAddress());
            if (isOreoOrAbove()) {
                hotspotControl.turnOnOreoHotspot(m_fileServer.getListeningPort());
            } else {
                /*
                 * We need create a Open Hotspot with an SSID which can be intercepted by Receiver.
                 * Here is the combination logic followed to create SSID for open Hotspot and same is followed by Receiver while decoding SSID, Sender HostName & port to connect
                 * Reason for doing this is to keep SSID unique, constant(unless port is assigned by system) and interpretable by Receiver
                 * {last 4 digits of android id} + {-} + Base64 of [{sender name} + {|} + SENDER_WIFI_NAMING_SALT + {|} + {port}]
                 */
                String androidId = Settings.Secure.ANDROID_ID;
                androidId = androidId.replaceAll("[^A-Za-z0-9]", "");
                String name = (androidId.length() > 4 ? androidId.substring(androidId.length() - 4) : androidId) + "-" + Base64.encodeToString((TextUtils.isEmpty(sender_name) ? generateP2PSpuulName() : sender_name + "|" + WifiUtils.SENDER_WIFI_NAMING_SALT + "|" + m_fileServer.getListeningPort()).getBytes(), Base64.DEFAULT);

                hotspotControl.turnOnPreOreoHotspot(name, m_fileServer.getListeningPort());
                hotspotCheckHandler.sendEmptyMessage(AP_START_CHECK);
            }
        } catch (Exception e) {
            Log.e(TAG, "exception in hotspot init: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String DEFAULT_SENDER_NAME = "Sender.";

    public static String generateP2PSpuulName() {
        String androidId = Settings.Secure.ANDROID_ID;
        if (TextUtils.isEmpty(androidId))
            androidId = "";
        else
            androidId = androidId.length() <= 3 ? androidId : androidId.substring(androidId.length() - 3, androidId.length());
        return DEFAULT_SENDER_NAME + androidId;
    }

    private void stopFileTasks() {
        try {
            if (null != m_fileServer && m_fileServer.isAlive()) {
                Log.d(TAG, "stopping server..");
                m_fileServer.stop();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "exception in stopping file server: " + e.getMessage());
        }
    }

    private void sendAcknowledgementBroadcast(int type) {
        Intent updateIntent = new Intent(SHARE_SERVER_UPDATES_INTENT_ACTION);
        updateIntent.putExtra(TYPE, type);
        sendBroadcast(updateIntent);
    }

    private void sendTransferStatusBroadcast(String ip, int progress, String updateText, String fileName) {
        Intent updateIntent = new Intent(SHARE_SERVER_UPDATES_INTENT_ACTION);
        updateIntent.putExtra(TYPE, FILE_TRANSFER_STATUS);
        updateIntent.putExtra(SHARE_SERVER_UPDATE_TEXT, updateText);
        updateIntent.putExtra(SHARE_SERVER_UPDATE_FILE_NAME, fileName);
        updateIntent.putExtra(SHARE_CLIENT_IP, ip);
        updateIntent.putExtra(SHARE_TRANSFER_PROGRESS, progress);
        sendBroadcast(updateIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (null != hotspotControl && hotspotControl.isEnabled()) {
            hotspotControl.disable();
            wifiManager.setWifiEnabled(true);
            stopFileTasks();
        }
        if (null != hotspotCheckHandler)
            hotspotCheckHandler.removeCallbacksAndMessages(null);
        if (null != m_notificationStopActionReceiver)
            unregisterReceiver(m_notificationStopActionReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private void updateForegroundNotification() {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(SHARE_SERVICE_NOTIFICATION_ID, getNotification(getString(R.string.p2p_sender_service_notification_header), true));
    }

    private static class HotspotChecker extends Handler {
        WeakReference<DittoService> service;

        HotspotChecker(DittoService senderService) {
            this.service = new WeakReference<>(senderService);
        }

        @Override
        public void handleMessage(Message msg) {
            DittoService senderService = service.get();
            if (null == senderService)
                return;
            if (msg.what == AP_ALIVE_CHECK) {
                if (null == senderService.hotspotControl || !senderService.hotspotControl.isEnabled()) {
                    Log.e(TAG, "hotspot isnt active, close this service");
                    senderService.disableHotspotAndStop();
                } else sendEmptyMessageDelayed(AP_ALIVE_CHECK, 3000);
            } else if (msg.what == AP_START_CHECK && null != senderService.hotspotControl) {
                if (senderService.hotspotControl.isEnabled())
                    senderService.updateForegroundNotification();
                else {
                    removeMessages(AP_START_CHECK);
                    sendEmptyMessageDelayed(AP_START_CHECK, 800);
                }
            }
        }
    }
}
