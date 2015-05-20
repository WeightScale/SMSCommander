package com.konst.sms_commander;

import android.telephony.SmsMessage;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Kostya on 20.05.2015.
 */
public class SmsCommander {
    OnSmsCommandListener onSmsCommandListener;

    /** Слушатель есть смс комманды.
     * @param listener
     */
    public void setOnSmsCommandListener(OnSmsCommandListener listener) {
        onSmsCommandListener = listener;
    }

    /** Проверяем является коммандой.
     * Если команда запускаем процесс парсинга пакета комманд.
     * @param codeword Ключ для декодирования сообщения.
     * @param objects PDUs сообщение.
     * @return true - это комманда.
     */
    public boolean isCommand(String codeword, Object[] objects){
        if (objects != null){
            StringBuilder bodyText = new StringBuilder();
            String address = "";
            for (int i = 0; i < objects.length; i++) {
                SmsMessage message = SmsMessage.createFromPdu((byte[]) objects[i]);
                address = message.getDisplayOriginatingAddress();
                bodyText.append(message.getMessageBody());
            }
            try {
                String textSent = SMS.decrypt(codeword, bodyText.toString());
                String date = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date());
                new Thread(new ParsingSmsCommand(address, textSent,date)).start();
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    /** Парсер пакета комманд.
     * Формат пакета [ [address] space [ [комманда 1] space [комманда 2] space [комманда n] ] ]
     * Формат комманды [ [имя комманды]=[параметр] ]
     * Формат параметра [ [[значение 1]-[параметр 2]]_[[значение 2]-[параметр 2]]_[[значение n]-[параметр n]] ]
     */
    class ParsingSmsCommand implements Runnable {
        final String mAddress;
        final StringBuilder mText;
        final String date;

        /** Конструктор парсера.
         * @param address Адресс отправителя.
         * @param msg Пакет комманд.
         * @param date Дата получения пакета. */
        ParsingSmsCommand(String address, String msg, String date) {
            mAddress = address;
            mText = new StringBuilder(msg);
            this.date = date;
        }

        @Override
        public void run() {

        }

    }
}
