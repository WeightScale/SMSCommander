package com.konst.sms_commander;

import android.telephony.SmsMessage;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 *  Класс обработки смс пакетов.
 *  @author Kostya
 */
public class SmsCommander {
    OnSmsCommandListener onSmsCommandListener;

    /** Слушатель если есть смс пакет комманд.
     *  @param listener Слушатель.
     */
    public void setOnSmsCommandListener(OnSmsCommandListener listener) {
        onSmsCommandListener = listener;
    }

    /** Проверяем является ли object пакетом команд.
     *  Декодируем object, определяем если команда, запускаем процесс парсинга пакета комманд.
     *  @param codeword Ключ для декодирования сообщения.
     *  @param objects PDUs сообщения.
     *  @return true - это комманда.
     */
    public boolean isCommand(String codeword, Object[] objects){
        if (objects != null){
            StringBuilder bodyText = new StringBuilder();
            String address = "";
            for (Object object : objects) {
                SmsMessage message = SmsMessage.createFromPdu((byte[]) object);
                address = message.getDisplayOriginatingAddress();
                bodyText.append(message.getMessageBody());
            }
            try {
                String textSent = SMS.decrypt(codeword, bodyText.toString());
                String date = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date());
                new Thread(new ParsingSmsCommand(address, textSent, date)).start();
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    /** Парсер пакета комманд.
     *  Формат пакета [ [address] space [ [комманда 1] space [комманда 2] space [комманда n] ] ]
     *  Формат комманды [ [имя комманды]=[параметр] ]
     *  Формат параметра [ [[значение 1]-[параметр 2]]_[[значение 2]-[параметр 2]]_[[значение n]-[параметр n]] ]
     */
    static class ParsingSmsCommand implements Runnable {
        String mAddress;
        final StringBuilder mPackage;
        final String date;

        /** Конструктор парсера.
         *  @param address Адресс отправителя.
         *  @param _package Пакет комманд.
         *  @param date Дата получения пакета. */
        ParsingSmsCommand(String address, String _package, String date) {
            mAddress = address;
            mPackage = new StringBuilder(_package);
            this.date = date;
        }

        @Override
        public void run() {
            if (mAddress == null)
                return;
            if (mPackage.indexOf(" ") != -1) {
                String packageAddress = mPackage.substring(0, mPackage.indexOf(" "));
                if (!packageAddress.isEmpty()) {
                    if (packageAddress.length() > mAddress.length()) {
                        packageAddress = packageAddress.substring(packageAddress.length() - mAddress.length(), packageAddress.length());
                    } else if (packageAddress.length() < mAddress.length()) {
                        mAddress = mAddress.substring(mAddress.length() - packageAddress.length(), mAddress.length());
                    }
                    if (mAddress.equals(packageAddress)) {
                        mPackage.delete(0, mPackage.indexOf(" ") + 1);
                        StringBuilder textSent = new StringBuilder();
                        try {
                            //SmsCommand command = new SmsCommand(getApplicationContext(), mPackage.toString());
                            //textSent = command.commandsExt();
                        } catch (Exception e) {
                            textSent.append(e.getMessage());
                        }
                        try {
                            //SMS.sendSMS(address, textSent.toString());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

    }
}
