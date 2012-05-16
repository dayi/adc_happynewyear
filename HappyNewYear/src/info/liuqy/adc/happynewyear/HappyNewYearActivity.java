package info.liuqy.adc.happynewyear;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;

public class HappyNewYearActivity extends Activity {
	enum Market {
		NORTH, SOUTH, ANY;
		@Override
		public String toString() {
			switch (this) {
			case NORTH:
				return "NC";
			case SOUTH:
				return "SC";
			case ANY:
				return "";
			default:
				return super.toString();
			}
		}
	};

	enum Language {
		CHINESE, ENGLISH, ANY;
		@Override
		public String toString() {
			switch (this) {
			case CHINESE:
				return "CN";
			case ENGLISH:
				return "EN";
			case ANY:
				return "";
			default:
				return super.toString();
			}
		}
	};

    private static final int MSG_READCONTACTS_FINISH = 0x101;

    Handler readContactsHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case MSG_READCONTACTS_FINISH:
                    startSendListActivityWithMessageList(msg.getData());
                    findViewById(R.id.gen_sendlist).setEnabled(true);
                    break;
            }
            super.handleMessage(msg);

        }
    };

    private void startSendListActivityWithMessageList(Bundle sendlist) {
        Spinner sp = (Spinner)this.findViewById(R.id.customer_carer);
        String cc = sp.getSelectedItem().toString();

        EditText et = (EditText)this.findViewById(R.id.sms_template);
        String tmpl = et.getText().toString();

        Intent i = new Intent(this, SendListActivity.class);
        i.putExtra(SENDLIST, sendlist);
        i.putExtra(CUSTOMER_CARER, cc);
        i.putExtra(SMS_TEMPLATE, tmpl);
        startActivity(i);
    }

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
	}

    class readContactsThread implements Runnable {
        Market market;
        Language lang;

        public readContactsThread(Market market, Language lang) {
            this.market = market;
            this.lang = lang;
        }

        @Override
        public void run() {

            Bundle sendlist = new Bundle();


            Cursor cursorId = getContentResolver().query(
                    ContactsContract.Contacts.CONTENT_URI, null, null, null, null);

            // 读取所有联系人ID(必须有电话号码)，加入到数组中
            List<String> contactIdList = new ArrayList<String>();
            while (cursorId.moveToNext()) {
                // retrieve phone numbers
                int phoneCount = cursorId.getInt(cursorId
                        .getColumnIndex(Contacts.HAS_PHONE_NUMBER));
                // only process contacts with phone numbers
                if (phoneCount>0) {
                    contactIdList.add(cursorId.getString(cursorId.getColumnIndex(Contacts._ID)));
                }
            }
            cursorId.close();

            // 遍历联系人ID数组 读取每个联系人的信息
            // attributes for the contact
            Set<String> attrs = new HashSet<String>();
            Bundle curSendList = new Bundle();
            for(String contactId : contactIdList) {
                attrs.clear();
                curSendList.clear();

                Cursor cursorInfo = getContentResolver().query(
                        Data.CONTENT_URI, null, Data.CONTACT_ID + "=?",
                        new String[] { contactId }, null);

                // only process contacts with nickname (the first one)
                if (cursorInfo.moveToFirst()) {
                    String nickname = cursorInfo.getString(cursorInfo
                            .getColumnIndex(Nickname.NAME));

                    do {
                        String type = cursorInfo.getString(
                                cursorInfo.getColumnIndex(ContactsContract.Data.MIMETYPE));

                        if (type.equals(Nickname.CONTENT_ITEM_TYPE)){
                            nickname = cursorInfo.getString(cursorInfo
                                    .getColumnIndex(Nickname.NAME));
                        }else if(type.equals(Note.CONTENT_ITEM_TYPE)){
                            String noteInfo = cursorInfo.getString(cursorInfo
                                    .getColumnIndex(Note.NOTE));
                            String[] fragments = noteInfo.toUpperCase().split(","); //FIXME better regex?
                            for (String attr : fragments) {
                                attrs.add(attr);
                            }
                        }else if(type.equals(Phone.CONTENT_ITEM_TYPE)){
                            String phoneNumber = cursorInfo.getString(cursorInfo
                                    .getColumnIndex(Phone.NUMBER));
                            phoneNumber = phoneNumber.replaceAll("-","");
                            int phoneType = cursorInfo.getInt(cursorInfo
                                    .getColumnIndex(Phone.TYPE));

                            if (isMobile(phoneNumber, phoneType)) {
                                curSendList.putString(phoneNumber, nickname);
                            }
                        }

                    } while(cursorInfo.moveToNext());

                    //set defaults
                    if (!attrs.contains(Market.NORTH.toString())
                            && !attrs.contains(Market.SOUTH.toString()))
                        attrs.add(Market.NORTH.toString());

                    if (!attrs.contains(Language.CHINESE.toString())
                            && !attrs.contains(Language.ENGLISH.toString()))
                        attrs.add(Language.CHINESE.toString());

                    // only process contacts with the matching market & language
                    if (attrs.contains("ADC") //FIXME for class demo only
                            && (market.equals(Market.ANY) || attrs.contains(market.toString()))
                            && (lang.equals(Language.ANY) || attrs.contains(lang.toString()))) {
                        sendlist.putAll(curSendList);
                    }
                }
            }
            Message message = new Message();
            message.what = MSG_READCONTACTS_FINISH;
            message.setData(sendlist);
           HappyNewYearActivity.this.readContactsHandler.sendMessage(message);
        }
    }
	/**
	 * Return all number ~ nickname pairs according to the rule. Be careful: the
	 * same numbers will be in only one pair.
	 * 
	 * @return <number, nickname>s
	 */
	public void readContacts(Market market, Language lang) {
        new Thread(new readContactsThread(market, lang)).start();
        findViewById(R.id.gen_sendlist).setEnabled(false);
	}

	// the tricky pattern for identifying Chinese mobile numbers
	static final Pattern MOBILE_PATTERN = Pattern.compile("(13|15|18)\\d{9}");

	public boolean isMobile(String number, int type) {
		if (type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) {
			Matcher m = MOBILE_PATTERN.matcher(number);
			
			if (m.find()) {
				return true;
			}
		}
		
		return false;
	}

    public static final String SENDLIST = "info.liuqy.adc.happynewyear.SENDLIST";
    public static final String CUSTOMER_CARER = "info.liuqy.adc.happynewyear.CUSTOMER_CARER";
    public static final String SMS_TEMPLATE = "info.liuqy.adc.happynewyear.SMS_TEMPLATE";   

    public void genSendlist(View v) {
        RadioGroup rg = (RadioGroup)this.findViewById(R.id.customer_group);
        int id = rg.getCheckedRadioButtonId();
        Market targetMarket = (id == R.id.btn_north) ? Market.NORTH : Market.SOUTH;

        rg = (RadioGroup)this.findViewById(R.id.customer_lang);
        id = rg.getCheckedRadioButtonId();
        Language targetLanguage = (id == R.id.btn_cn) ? Language.CHINESE : Language.ENGLISH;


        readContacts(targetMarket, targetLanguage);


    }

}