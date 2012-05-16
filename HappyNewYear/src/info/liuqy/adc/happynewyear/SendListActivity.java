package info.liuqy.adc.happynewyear;

import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.ListActivity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.SmsManager;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.SimpleAdapter;
import android.widget.Toast;

public class SendListActivity extends ListActivity {
	
    static final String KEY_TO = "TO";
    static final String KEY_SMS = "SMS";
    static final String KEY_STATE = "STATE";

    static final String SMS_STATE_NOT_SEND  = "0";
    static final String SMS_STATE_SENDED    = "1";
    static final String SMS_STATE_DELIVERED = "2";

    static final String SENT_ACTION = "SMS_SENT_ACTION";
    static final String DELIVERED_ACTION = "SMS_DELIVERED_ACTION";
    static final String EXTRA_IDX = "contact_adapter_idx";
    static final String EXTRA_TONUMBER = "sms_to_number";
    static final String EXTRA_SMS = "sms_content";
    
    private static final int HAPPYNEWYEAR_ID = 1;

    private static final String DB_NAME = "data";
    private static final int DB_VERSION = 2;
    
    private static final String TBL_NAME = "sms";
    static final String FIELD_TO = "to_number";
    static final String FIELD_SMS = "sms";
    static final String FIELD_STATE = "state";
    static final String KEY_ROWID = "_id";
    
    //[<TO, number>,<SMS, sms>]
    List<Map<String, String>> smslist = new LinkedList<Map<String, String>>();
    SimpleAdapter adapter;

    static BroadcastReceiver smsSentReceiver = null;
	static BroadcastReceiver smsDeliveredReceiver = null;
    
    SQLiteOpenHelper dbHelper = null;
    SQLiteDatabase db = null;

    protected static final int SMSLIST_SEND_START = 0x101;
    protected static final int SMSLIST_SEND_FINISH = 0x102;
    protected static final int SMS_SEND_START = 0x103;
    protected static final int SMS_SEND_SUCCESSED = 0x104;
    protected static final int SMS_SEND_DELIVERED = 0x106;

    protected static final int NOTIFICATION_ID = 0x110;

    private NotificationManager notificationManager;
    private RemoteViews remoteViews;
    private PendingIntent pIntent;
    private Notification notification;

    private Integer nSended = 0;

    void showNotification(){

        notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        remoteViews = new RemoteViews(getPackageName(), R.layout.progressdlg);
        notification = new Notification(R.drawable.ic_launcher, "a", System.currentTimeMillis());

        notification.icon = R.drawable.ic_launcher;
        remoteViews.setImageViewResource(R.id.image, R.drawable.ic_launcher);
        remoteViews.setProgressBar(R.id.progressBar, smslist.size(), nSended, false);
        remoteViews.setTextViewText(R.id.textView, nSended+"/"+smslist.size());
        notification.contentView = remoteViews;

        notification.contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, SendListActivity.class), 0);

        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    void updateNotification(){
        remoteViews.setProgressBar(R.id.progressBar, smslist.size(), nSended, false);
        remoteViews.setTextViewText(R.id.textView, nSended+"/"+smslist.size());
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    Handler sendSmsHandler = new Handler() {
        public void handleMessage(Message msg) {
            int idx = -1;
            switch (msg.what) {
                case SMSLIST_SEND_START:
                    findViewById(R.id.send).setEnabled(false);
                    nSended = 0;
                    showNotification();
                    break;
                case SMSLIST_SEND_FINISH:
                    findViewById(R.id.send).setEnabled(true);
                    break;

                case SMS_SEND_START:
                    idx = msg.getData().getInt(EXTRA_IDX);
                    getListView().getChildAt(idx).setBackgroundColor(Color.BLUE);

                    break;

                case SMS_SEND_SUCCESSED:
                    idx = msg.getData().getInt(EXTRA_IDX);
                    getListView().getChildAt(idx).setBackgroundColor(Color.YELLOW);
                    smslist.get(idx).put(KEY_STATE, SMS_STATE_SENDED);
                    nSended++;
                    updateNotification();
                    break;

                case SMS_SEND_DELIVERED:
                    idx = msg.getData().getInt(EXTRA_IDX);
                    getListView().getChildAt(idx).setBackgroundColor(Color.RED);
                    smslist.get(idx).put(KEY_STATE, SMS_STATE_DELIVERED);
                    break;

            }
            super.handleMessage(msg);
        }
    };

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sendlist);
        
        initdb();
        createReceivers();
        
        adapter = new SimpleAdapter(this, smslist,
                android.R.layout.simple_list_item_2,
                new String[]{KEY_TO, KEY_SMS},
                new int[]{android.R.id.text1, android.R.id.text2});
        this.setListAdapter(adapter);
        handleIntent();
        
        if (smslist.size() == 0) {  //FIXME need a better judge if from notification
            loadFromDatabase();
        } else {
            updateListItemState();
        }
    }

    private void updateListItemState(){
        Cursor cur = db.query(TBL_NAME, new String[]{KEY_ROWID, FIELD_TO, FIELD_SMS, FIELD_STATE},
                null, null, null, null, null);

        while (cur.moveToNext()) {
            String toNumber = cur.getString(cur.getColumnIndex(FIELD_TO));
            String sms = cur.getString(cur.getColumnIndex(FIELD_SMS));
            String state = cur.getString(cur.getColumnIndex(FIELD_STATE));
            for(Map<String, String> rec:smslist){
                if (rec.get(KEY_TO).equals(toNumber)){
                    rec.put(KEY_STATE, state);
                }
            }
        }

        cur.close();
    }
	
	public void handleIntent() {
        Bundle data = this.getIntent().getExtras();
        if (data != null) {
            Bundle sendlist = data.getParcelable(HappyNewYearActivity.SENDLIST);
            
            String cc = data.getString(HappyNewYearActivity.CUSTOMER_CARER);
            String tmpl = data.getString(HappyNewYearActivity.SMS_TEMPLATE);
            
            tmpl = tmpl.replaceAll("\\{FROM\\}", cc);
            
            for (String n : sendlist.keySet()) {
                String sms = tmpl.replaceAll("\\{TO\\}", sendlist.getString(n));
                Map<String, String> rec = new Hashtable<String, String>();
                rec.put(KEY_TO, n);
                rec.put(KEY_SMS, sms);
                rec.put(KEY_STATE, SMS_STATE_NOT_SEND);
                smslist.add(rec);
                adapter.notifyDataSetChanged();
                saveToDatabase(sendlist.getString(n), sms);
            }
        }
	}

    class sendSmsThread implements Runnable {
        public void run() {

            Message message = new Message();
            message.what = SendListActivity.SMSLIST_SEND_START;

            SendListActivity.this.sendSmsHandler.sendMessage(message);
            try {
                SmsManager sender = SmsManager.getDefault();
                if (sender == null) {
                    // TODO toast error msg
                    Toast.makeText(SendListActivity.this,
                            "can't get SmsManager!", Toast.LENGTH_SHORT)
                            .show();
                }

                for (int idx = 0; idx < smslist.size(); idx++) {
                    Map<String, String> rec = smslist.get(idx);
                    String toNumber = rec.get(KEY_TO);
                    String sms = rec.get(KEY_SMS);
                    String state = rec.get(KEY_STATE);

                    Bundle bundle = new Bundle();
                    bundle.putInt(EXTRA_IDX, idx);
                    bundle.putString(EXTRA_TONUMBER, toNumber);
                    bundle.putString(EXTRA_SMS, sms);

                    if (state.equals(SMS_STATE_NOT_SEND)){
                        // SMS sent pending intent
                        Intent sentActionIntent = new Intent(SENT_ACTION);
                        sentActionIntent.putExtras(bundle);
                        PendingIntent sentPendingIntent = PendingIntent.getBroadcast(
                                SendListActivity.this, 0, sentActionIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT);

                        // SMS delivered pending intent
                        Intent deliveredActionIntent = new Intent(DELIVERED_ACTION);
                        deliveredActionIntent.putExtras(bundle);
                        PendingIntent deliveredPendingIntent = PendingIntent.getBroadcast(
                                SendListActivity.this, 0, deliveredActionIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT);

                        //send
                        Message msg = new Message();
                        msg.what = SMS_SEND_START;
                        msg.setData(bundle);
                        SendListActivity.this.sendSmsHandler.sendMessage(msg);
                        sender.sendTextMessage(toNumber, null, sms, sentPendingIntent,
                                deliveredPendingIntent);

                        if (idx<smslist.size()-1){
                            Thread.sleep(5000);
                        }
                    }else {
                        Message msg = new Message();

                        if(state.equals(SMS_STATE_SENDED)){
                            msg.what = SMS_SEND_SUCCESSED;
                        }else{
                            msg.what = SMS_SEND_DELIVERED;
                        }
                        msg.setData(bundle);
                        SendListActivity.this.sendSmsHandler.sendMessage(msg);
                    }
                }
            } catch (InterruptedException e) {

            }

            message = new Message();
            message.what = SendListActivity.SMSLIST_SEND_FINISH;
            SendListActivity.this.sendSmsHandler.sendMessage(message);
        }
    }

    public void sendSms(View v) {
        new Thread(new sendSmsThread()).start();

    }

	@Override
	protected void onStart() {
		super.onStart();
		// Question for you: where is the right place to register receivers?
		registerReceivers();
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		// Question for you: where is the right place to unregister receivers?
		unregisterReceivers();
	}
	
	protected void createReceivers() {
		if (smsSentReceiver == null)
			smsSentReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					int idx = intent.getIntExtra(EXTRA_IDX, -1);
					String toNum = intent.getStringExtra(EXTRA_TONUMBER);
					String sms = intent.getStringExtra(EXTRA_SMS);
					int succ = getResultCode();
					if (succ == Activity.RESULT_OK) {
                        Message msg = new Message();
                        msg.what = SMS_SEND_SUCCESSED;
                        msg.setData(intent.getExtras());
                        sendSmsHandler.sendMessage(msg);
                        updateSmsState(toNum, SMS_STATE_SENDED);
					} else {
						// TODO
					}
				}
			};

		if (smsDeliveredReceiver == null)
			smsDeliveredReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					int idx = intent.getIntExtra(EXTRA_IDX, -1);
					String toNum = intent.getStringExtra(EXTRA_TONUMBER);
					String sms = intent.getStringExtra(EXTRA_SMS);
					int succ = getResultCode();
					if (succ == Activity.RESULT_OK) {
                        Message msg = new Message();
                        msg.what = SMS_SEND_DELIVERED;
                        msg.setData(intent.getExtras());
                        sendSmsHandler.sendMessage(msg);
                        updateSmsState(toNum, SMS_STATE_DELIVERED);
					} else {
						// TODO
					}
				}
			};
	}

	protected void registerReceivers() {
		this.registerReceiver(smsSentReceiver, new IntentFilter(SENT_ACTION));
		this.registerReceiver(smsDeliveredReceiver, new IntentFilter(DELIVERED_ACTION));
	}
	
	protected void unregisterReceivers() {
		this.unregisterReceiver(smsSentReceiver);
		this.unregisterReceiver(smsDeliveredReceiver);
	}
	
    public void notifySuccessfulDelivery(String title, String text) {
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
        
        int icon = R.drawable.ic_launcher;
        CharSequence tickerText = "HappyNewYear";
        long when = System.currentTimeMillis();
        Notification notification = new Notification(icon, tickerText, when);
        
        Context context = getApplicationContext();
        CharSequence contentTitle = title;
        CharSequence contentText = text;
        Intent notificationIntent = new Intent(this, SendListActivity.class); //if click, then open SendListActivity
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

        notification.defaults |= Notification.DEFAULT_SOUND;
        notification.defaults |= Notification.DEFAULT_VIBRATE;
        notification.defaults |= Notification.DEFAULT_LIGHTS;
        
        mNotificationManager.notify(HAPPYNEWYEAR_ID, notification);
    }

    protected void initdb() {
        dbHelper = new SQLiteOpenHelper(this, DB_NAME, null, DB_VERSION) {
            @Override
            public void onCreate(SQLiteDatabase db) {
                db.execSQL("create table sms (_id integer primary key autoincrement, " +
                        "to_number text not null, sms text not null, state text not null )");
            }
            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVer, int newVer) {
                //TODO on DB upgrade
            }
            
        };
        
        db = dbHelper.getWritableDatabase();
    }
    
    protected void loadFromDatabase() {
        Cursor cur = db.query(TBL_NAME, new String[]{KEY_ROWID, FIELD_TO, FIELD_SMS, FIELD_STATE},
                null, null, null, null, null);

        while (cur.moveToNext()) {
            String toNumber = cur.getString(cur.getColumnIndex(FIELD_TO));
            String sms = cur.getString(cur.getColumnIndex(FIELD_SMS));
            String state = cur.getString(cur.getColumnIndex(FIELD_STATE));
            Map<String, String> rec = new Hashtable<String, String>();
            rec.put(KEY_TO, toNumber);
            rec.put(KEY_SMS, sms);
            rec.put(KEY_STATE, state);
            smslist.add(rec);
        }
        
        cur.close();
        
        adapter.notifyDataSetChanged();
    }
    
    protected void saveToDatabase(String toNum, String sms) {
        ContentValues values = new ContentValues();
        values.put(FIELD_TO,  toNum); //FIXME string constant
        values.put(FIELD_SMS, sms);
        values.put(FIELD_STATE,SMS_STATE_NOT_SEND);
        Cursor cur = db.query(TBL_NAME, null, String.format("%s = ?", FIELD_TO), new String[]{toNum}, null, null, null);
        if (cur.moveToFirst() == false){
            cur.close();
            db.insert(TBL_NAME, null, values);
        }else{
            cur.close();
        }
    }

    protected void updateSmsState(String toNum, String state) {
        ContentValues values = new ContentValues();
        values.put(FIELD_STATE, state);
        db.update(TBL_NAME, values, String.format("%s=?", FIELD_TO), new String[]{toNum});
    }
    
}
