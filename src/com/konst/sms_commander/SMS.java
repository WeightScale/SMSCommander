package com.konst.sms_commander;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.telephony.SmsManager;
import android.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*Column ID    -   Column Name

        0           :     _id
        1           :     thread_id
        2           :     address
        3           :     person
        4           :     date
        5           :     protocol
        6           :     read
        7           :     status
        8           :     type
        9           :     reply_path_present
        10          :     subject
        11          :     body
        12          :     service_center
        13          :     locked*/

/*
 * Created by Kostya on 29.03.2015.
 */
class SMS {
    final Context mContext;
    private static final String CIPHER_ALGORITHM = "AES";
    private static final String RANDOM_GENERATOR_ALGORITHM = "SHA1PRNG";
    private static final int RANDOM_KEY_SIZE = 128;

    final String _ID = "_id";
    final String THREAD_ID = "thread_id";
    final String ADDRESS = "address";
    final String PERSON = "person";
    final String DATE = "date";
    final String PROTOCOL = "protocol";
    final String READ = "read";
    final String STATUS = "status";
    final String TYPE = "type";
    final String REPLY_PATH_PRESENT = "reply_path_present";
    final String BODY = "body";
    final String SERVICE_CENTER = "service_center";
    final String LOCKED = "locked";


    public SMS(Context context) {
        mContext = context;
    }

    public List<SmsObject> getAllSms() {
        Uri message = Uri.parse("content://sms/");
        ContentResolver contentResolver = mContext.getContentResolver();

        Cursor c = contentResolver.query(message, null, null, null, null);
        ContentQueryMap mQueryMap = new ContentQueryMap(c, BaseColumns._ID, true, null);
        Map<String, ContentValues> map = mQueryMap.getRows();
        List<SmsObject> list = new ArrayList<>();

        for (Map.Entry<String, ContentValues> entry : map.entrySet()) {
            SmsObject smsObject = new SmsObject();
            smsObject.setId(entry.getKey());
            if (entry.getValue().containsKey("body")) {
                smsObject.setMsg(entry.getValue().getAsString("body"));
            }
            if (entry.getValue().containsKey("address")) {
                smsObject.setAddress(entry.getValue().getAsString("address"));
            }
            if (entry.getValue().containsKey("date")) {
                smsObject.setTime(entry.getValue().getAsString("date"));
            }
            list.add(smsObject);
        }
        c.close();

        return list;
    }

    public synchronized List<SmsObject> getInboxSms() {
        Uri message = Uri.parse("content://sms/inbox");
        ContentResolver contentResolver = mContext.getContentResolver();

        Cursor c = contentResolver.query(message, null, null, null, null);
        ContentQueryMap mQueryMap = new ContentQueryMap(c, BaseColumns._ID, true, null);
        Map<String, ContentValues> map = mQueryMap.getRows();
        List<SmsObject> list = new ArrayList<>();

        for (Map.Entry<String, ContentValues> entry : map.entrySet()) {
            SmsObject smsObject = new SmsObject();
            smsObject.setId(entry.getKey());
            if (entry.getValue().containsKey("body")) {
                smsObject.setMsg(entry.getValue().getAsString("body"));
            }
            if (entry.getValue().containsKey("address")) {
                smsObject.setAddress(entry.getValue().getAsString("address"));
            }
            if (entry.getValue().containsKey("date")) {
                smsObject.setTime(entry.getValue().getAsString("date"));
            }
            list.add(smsObject);
        }
        c.close();

        return list;
    }

    public List<SmsObject> getInboxSms(String name) {
        Uri message = Uri.parse("content://sms/inbox");
        ContentResolver contentResolver = mContext.getContentResolver();

        Cursor c = contentResolver.query(message, null, null, null, null);
        ContentQueryMap mQueryMap = new ContentQueryMap(c, BaseColumns._ID, true, null);
        Map<String, ContentValues> map = mQueryMap.getRows();
        List<SmsObject> list = new ArrayList<>();

        for (Map.Entry<String, ContentValues> entry : map.entrySet()) {
            if (name.equals(entry.getValue().get(ADDRESS))) {
                SmsObject smsObject = new SmsObject();
                smsObject.setId(entry.getKey());
                smsObject.setMsg(entry.getValue().getAsString(BODY));
                smsObject.setAddress(entry.getValue().getAsString(ADDRESS));
                smsObject.setTime(entry.getValue().getAsString(DATE));
                list.add(smsObject);
            }
        }
        c.close();

        return list;
    }

    public synchronized int delete(int smsId) {
        final Uri smsUri = ContentUris.withAppendedId(Uri.parse("content://sms/"), smsId);
        return mContext.getContentResolver().delete(smsUri, null, null);
    }

    public Map<String, ContentValues> getSentSms() {
        Uri message = Uri.parse("content://sms/sent");
        ContentResolver contentResolver = mContext.getContentResolver();

        Cursor c = contentResolver.query(message, null, null, null, null);
        ContentQueryMap mQueryMap = new ContentQueryMap(c, BaseColumns._ID, true, null);
        Map<String, ContentValues> map = mQueryMap.getRows();
        c.close();

        return map;
    }

    public Map<String, ContentValues> getDraftSms() {
        Uri message = Uri.parse("content://sms/draft");
        ContentResolver contentResolver = mContext.getContentResolver();

        Cursor c = contentResolver.query(message, null, null, null, null);
        ContentQueryMap mQueryMap = new ContentQueryMap(c, BaseColumns._ID, true, null);
        Map<String, ContentValues> map = mQueryMap.getRows();
        c.close();

        return map;
    }

    // Encrypts string and encodes in Base64
    public static String encrypt(String password, String data) throws Exception {
        byte[] secretKey = generateKey(password.getBytes());
        byte[] clear = data.getBytes();

        Key secretKeySpec = new SecretKeySpec(secretKey, CIPHER_ALGORITHM);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);

        byte[] encrypted = cipher.doFinal(clear);

        return Base64.encodeToString(encrypted, Base64.DEFAULT);
    }

    // Decrypts string encoded in Base64
    public static String decrypt(String password, String encryptedData) throws Exception {
        byte[] secretKey = generateKey(password.getBytes());
        Key secretKeySpec = new SecretKeySpec(secretKey, CIPHER_ALGORITHM);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);

        byte[] encrypted = Base64.decode(encryptedData, Base64.DEFAULT);
        byte[] decrypted = cipher.doFinal(encrypted);

        return new String(decrypted);
    }

    public static byte[] generateKey(byte... seed) throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(CIPHER_ALGORITHM);
        SecureRandom secureRandom = SecureRandom.getInstance(RANDOM_GENERATOR_ALGORITHM);
        secureRandom.setSeed(seed);
        keyGenerator.init(RANDOM_KEY_SIZE, secureRandom);
        SecretKey secretKey = keyGenerator.generateKey();
        return secretKey.getEncoded();
    }

    //==================================================================================================================
    public static void sendSMS(String phoneNumber, String message) throws Exception {
        SmsManager sms = SmsManager.getDefault();
        ArrayList<String> parts = sms.divideMessage(message);
        sms.sendMultipartTextMessage(phoneNumber, null, parts, null, null);
    }

    public class SmsObject {
        private String _id;
        private String _address;
        private String _msg;
        private String _time;

        public String getId() {
            return _id;
        }

        public String getAddress() {
            return _address;
        }

        public String getMsg() {
            return _msg;
        }

        public String getTime() {
            return _time;
        }

        public void setId(String id) {
            _id = id;
        }

        public void setAddress(String address) {
            _address = address;
        }

        public void setMsg(String msg) {
            _msg = msg;
        }

        public void setTime(String time) {
            _time = time;
        }

    }
}
